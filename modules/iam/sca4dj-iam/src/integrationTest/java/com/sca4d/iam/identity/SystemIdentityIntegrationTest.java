package com.sca4d.iam.identity;

import static com.sca4d.core.api.name.NameFormat.COLON;
import static com.sca4d.core.api.name.NameFormat.DOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        configuration.addMapper(SystemIdentityMapper.class);
        configuration.addMapper(IdentityMapper.class);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(
                new ClassPathResource("mapper/iam/SystemIdentityMapper.xml"),
                new ClassPathResource("mapper/iam/IdentityMapper.xml"));
        factory.afterPropertiesSet();
        SqlSessionTemplate sessions = new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        repository = new SystemIdentityRepository(
                sessions.getMapper(SystemIdentityMapper.class),
                sessions.getMapper(IdentityMapper.class),
                new SqlBatches(new SqlBatchProperties()));
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
    }

    @Test
    void commitsTheCompleteIdentityTableBeforeSmartLifecycleStarts() {
        SystemIdentityProperties properties = new SystemIdentityProperties();
        properties.setSystemPrincipalIds(Map.of("MAIN", 1L, "IOT", 2L));
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
        var confirmed = transaction.execute(status -> repository.register(Map.of(SystemPrincipals.MAIN, 1L, iot, 2L)));
        assertThat(confirmed).isEqualTo(SystemPrincipals.snapshot());
        assertThatThrownBy(
                () -> transaction.execute(status -> repository.register(Map.of(SystemPrincipals.MAIN, 1L, iot, 3L))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("人工迁移");
        assertThat(SystemPrincipals.require(iot).principalId()).isEqualTo(2);
    }

    @Test
    void rejectsOccupiedIdsAndRollsBackEveryNewRow() {
        jdbc.update(
                "INSERT INTO iam_principal(id, principal_type, status) VALUES (7, 'iam.platform-principal', 'ACTIVE')");
        assertThatThrownBy(
                () -> transaction.execute(
                        status -> repository.register(
                                Map.of(SystemPrincipals.MAIN, 1L, TreeCode.ofSegment("IOT"), 7L))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("其他 ID");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_subject", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_system_identity", Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT id FROM iam_principal", Long.class)).containsExactly(7L);
    }

    /** 通过另一条实际数据库连接确认提交后才允许启动工作线程。 */
    private static class IdentityProbe implements SmartLifecycle {
        private final JdbcTemplate jdbc;

        private final AtomicBoolean started;

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
