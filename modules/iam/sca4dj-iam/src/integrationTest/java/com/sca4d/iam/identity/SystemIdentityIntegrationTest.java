package com.sca4d.iam.identity;

import static com.sca4d.core.api.name.NameFormat.COLON;
import static com.sca4d.core.api.name.NameFormat.DOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SystemPrincipals;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.runtime.InstanceCapability;
import com.sca4d.core.spring.persistence.batch.SqlBatchProperties;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.core.spring.persistence.mybatis.CoreEnumTypeHandler;
import com.sca4d.core.spring.persistence.mybatis.StringValueTypeHandler;
import com.sca4d.iam.config.SystemIdentityInitializer;
import com.sca4d.iam.config.SystemIdentityProperties;
import com.sca4d.iam.identity.mapper.IdentityMapper;
import com.sca4d.iam.identity.mapper.SystemIdentityMapper;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** 使用临时 PostgreSQL 验证系统身份首次登记、冲突回滚、固定 ID 和工作线程启动顺序。 */
@Testcontainers
class SystemIdentityIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private JdbcTemplate jdbc;

    private SystemIdentityRepository repository;

    private IdentityRepository identities;

    private TransactionTemplate transaction;

    /** 每个用例使用容器内的空 schema，不连接开发数据库。 */
    @BeforeEach
    void createSchema() throws Exception {
        DriverManagerDataSource source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        ResourceDatabasePopulator schema = new ResourceDatabasePopulator(
                new ClassPathResource("META-INF/sca4dj/schema/dictionary-settings.sql"),
                new ClassPathResource("META-INF/sca4dj/schema/iam.sql"));
        schema.setSeparator(ScriptUtils.EOF_STATEMENT_SEPARATOR);
        schema.execute(source);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(source);
        Configuration configuration = new Configuration();
        configuration.setDefaultEnumTypeHandler(CoreEnumTypeHandler.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.getTypeHandlerRegistry()
                .register(
                        TreeCode.class,
                        JdbcType.VARCHAR,
                        new StringValueTypeHandler<>(
                                value -> value.indexOf(':') < 0 ? TreeCode.ofSegment(value) : TreeCode.ofColon(value),
                                value -> value.format(COLON)));
        configuration.getTypeHandlerRegistry()
                .register(
                        PrincipalType.class,
                        JdbcType.VARCHAR,
                        new StringValueTypeHandler<>(PrincipalType::new, value -> value.format(DOT)));
        configuration.getTypeHandlerRegistry()
                .register(
                        com.sca4d.core.api.identity.RealmKey.class,
                        JdbcType.VARCHAR,
                        new StringValueTypeHandler<>(
                                com.sca4d.core.api.identity.RealmKey::new,
                                com.sca4d.core.api.identity.RealmKey::value));
        configuration.getTypeHandlerRegistry()
                .register(
                        com.sca4d.core.api.identity.AccountIdentifierType.class,
                        JdbcType.VARCHAR,
                        new StringValueTypeHandler<>(
                                com.sca4d.core.api.identity.AccountIdentifierType::new,
                                value -> value.format(COLON)));
        configuration.addMapper(SystemIdentityMapper.class);
        configuration.addMapper(IdentityMapper.class);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(
                new ClassPathResource("mapper/iam/SystemIdentityMapper.xml"),
                new ClassPathResource("mapper/iam/IdentityMapper.xml"));
        factory.afterPropertiesSet();
        SqlSessionTemplate sessions = new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        identities = new IdentityRepository(
                sessions.getMapper(IdentityMapper.class),
                new SqlBatches(new SqlBatchProperties()));
        repository = new SystemIdentityRepository(
                sessions.getMapper(SystemIdentityMapper.class),
                sessions.getMapper(IdentityMapper.class),
                new SqlBatches(new SqlBatchProperties()));
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
    }

    @Test
    void batchAccountsPreserveSubjectBoundariesAndIdentifierUpdatesReturnDatabaseState() {
        jdbc.update(
                "INSERT INTO iam_subject (id,subject_type,status) VALUES (101,'DEVICE','ACTIVE'),(102,'HUMAN','DISABLED'),(103,'DEVICE','ACTIVE')");
        jdbc.update(
                "INSERT INTO iam_account (id,subject_id,realm_key,status) VALUES (201,101,'iot-device','ACTIVE'),(202,102,'platform','DISABLED'),(203,101,'platform','ACTIVE')");
        jdbc.update("UPDATE iam_subject SET deleted=1 WHERE id=103");
        assertThat(identities.findSubjects(List.of(101L, 102L, 103L, 999L))).containsOnlyKeys(101L, 102L);
        var accounts = identities.findAccounts(List.of(101L, 102L, 999L, 101L));
        assertThat(accounts.get(101L)).extracting(com.sca4d.core.api.identity.Account::accountId)
                .containsExactly(201L, 203L);
        assertThat(accounts.get(102L)).extracting(com.sca4d.core.api.identity.Account::accountId).containsExactly(202L);
        assertThat(accounts.get(999L)).isEmpty();
        assertThat(identities.findAccounts(List.of())).isEmpty();
        jdbc.update(
                "INSERT INTO iam_account_identifier (id,account_id,realm_key,identifier_type,value,normalized_value,status) VALUES (301,201,'iot-device','USERNAME','before','before','DISABLED')");
        var changed = identities.changeIdentifierValue(301L, "After", "after").orElseThrow();
        assertThat(changed.value()).isEqualTo("After");
        assertThat(changed.normalizedValue()).isEqualTo("after");
        assertThat(changed.status()).isEqualTo(IdentityStatus.DISABLED);
        assertThat(identities.changeIdentifierValue(999L, "missing", "missing")).isEmpty();
        assertThat(identities.updateIdentifierStatus(301L, IdentityStatus.ACTIVE)).isTrue();
        assertThat(identities.findIdentifier(301L)).hasValueSatisfying(identifier -> {
            assertThat(identifier.status()).isEqualTo(IdentityStatus.ACTIVE);
            assertThat(identifier.value()).isEqualTo("After");
            assertThat(identifier.normalizedValue()).isEqualTo("after");
        });
        assertThat(identities.deleteIdentifier(301L)).isTrue();
        assertThat(identities.findIdentifier(301L)).isEmpty();
        assertThat(identities.changeIdentifierValue(301L, "deleted", "deleted")).isEmpty();
        assertThat(identities.updateIdentifierStatus(301L, IdentityStatus.ACTIVE)).isFalse();
        assertThat(identities.deleteIdentifier(301L)).isFalse();
    }

    @Test
    void commitsTheCompleteIdentityTableBeforeSmartLifecycleStarts() {
        SystemIdentityProperties properties = new Binder(
                new MapConfigurationPropertySource(
                        Map.of("sca4dj.iam.system-principal-ids[IOT]", "2")))
                .bind("sca4dj.iam", Bindable.of(SystemIdentityProperties.class))
                .get();
        TreeCode iot = TreeCode.ofSegment("IOT");
        SystemIdentityInitializer initializer = new SystemIdentityInitializer(
                repository,
                transaction,
                properties,
                List.of(new InstanceCapability(TreeCode.ofColon("IOT:API"), iot, List.of("/iot"))));
        AtomicBoolean started = new AtomicBoolean();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(SystemIdentityInitializer.class, () -> initializer);
            context.registerBean(IdentityProbe.class, () -> new IdentityProbe(jdbc, started));
            context.refresh();
            assertThat(started).isTrue();
            assertThat(SystemPrincipals.mainPrincipal().principalId()).isEqualTo(1);
            assertThat(SystemPrincipals.require(iot).principalId()).isEqualTo(2);
            assertThat(
                    jdbc.queryForObject("SELECT COUNT(*) FROM iam_system_identity WHERE subject_id = 1", Integer.class))
                    .isEqualTo(2);
        }
        var confirmed = transaction
                .execute(status -> repository.register(1L, Map.of(SystemPrincipals.MAIN, 1L, iot, 2L)));
        assertThat(confirmed).isEqualTo(SystemPrincipals.snapshot());
        assertThatThrownBy(
                () -> transaction
                        .execute(status -> repository.register(1L, Map.of(SystemPrincipals.MAIN, 1L, iot, 3L))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("人工迁移");
        assertThat(SystemPrincipals.require(iot).principalId()).isEqualTo(2);
    }

    @Test
    void rejectsOccupiedIdsAndRollsBackEveryNewRow() {
        jdbc.update(
                "INSERT INTO iam_principal(id, principal_type, status) VALUES (7, 'iam.platform-principal', 'ACTIVE')");
        long publishedSubjectId = Subject.SYSTEM.subjectId();
        SystemIdentityProperties properties = new SystemIdentityProperties();
        properties.setSystemSubjectId(publishedSubjectId + 100);
        properties.setSystemPrincipalIds(Map.of("MAIN", 1L, "IOT", 7L));
        SystemIdentityInitializer initializer = new SystemIdentityInitializer(
                repository,
                transaction,
                properties,
                List.of());
        assertThatThrownBy(initializer::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("其他 ID");
        assertThat(Subject.SYSTEM.subjectId()).isEqualTo(publishedSubjectId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_subject", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_system_identity", Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT id FROM iam_principal", Long.class)).containsExactly(7L);
    }

    /** 通过另一条实际数据库连接确认提交后才允许启动工作线程。 */
    private static class IdentityProbe implements SmartLifecycle {
        private final AtomicBoolean started;

        private final JdbcTemplate jdbc;

        private IdentityProbe(JdbcTemplate jdbc, AtomicBoolean started) {
            this.jdbc = jdbc;
            this.started = started;
        }

        @Override
        public void start() {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_system_identity", Integer.class)).isEqualTo(2);
            assertThat(SystemPrincipals.snapshot()).hasSize(2);
            started.set(true);
        }

        @Override
        public void stop() {
            started.set(false);
        }

        @Override
        public boolean isRunning() {
            return started.get();
        }
    }
}
