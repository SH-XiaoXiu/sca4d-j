package com.sca4d.auth.totp.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.auth.totp.credential.mapper.TotpCredentialMapper;
import com.sca4d.core.api.concurrent.OptimisticLockException;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.spring.persistence.mybatis.AffectedRowsAspect;
import com.sca4d.core.spring.persistence.mybatis.AffectedRowsInterceptor;
import com.sca4d.core.spring.persistence.mybatis.CoreEnumTypeHandler;
import com.sca4d.core.spring.persistence.mybatis.LongValueTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Objects;

/** 用隔离 PostgreSQL 验证凭据版本、状态及所属账号边界。 */
@Testcontainers
class TotpCredentialPersistenceIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static AnnotationConfigApplicationContext context;

    private static TotpCredentialRepository credentials;

    private static JdbcTemplate jdbc;

    /** 启用 Repository 的实际事务和影响行数检查。 */
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableAspectJAutoProxy
    static class Transactions {
    }

    /** 加载正式表结构和 Mapper，并注册凭据枚举与版本映射。 */
    @BeforeAll
    static void initialize() throws Exception {
        DriverManagerDataSource source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        ResourceDatabasePopulator schema = new ResourceDatabasePopulator(
                new ClassPathResource("META-INF/sca4dj/schema/dictionary-settings.sql"),
                new ClassPathResource("META-INF/sca4dj/schema/auth-totp.sql"));
        schema.setSeparator(ScriptUtils.EOF_STATEMENT_SEPARATOR);
        schema.execute(source);
        jdbc = new JdbcTemplate(source);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(source);
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setDefaultEnumTypeHandler(CoreEnumTypeHandler.class);
        configuration.getTypeHandlerRegistry()
                .register(Version.class, JdbcType.BIGINT, new LongValueTypeHandler<>(Version::new, Version::value));
        factory.setConfiguration(configuration);
        factory.setPlugins(new AffectedRowsInterceptor());
        factory.setMapperLocations(new ClassPathResource("mapper/auth/TotpCredentialMapper.xml"));
        SqlSessionTemplate template = new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        context = new AnnotationConfigApplicationContext();
        context.register(Transactions.class);
        context.registerBean(DataSourceTransactionManager.class, () -> new DataSourceTransactionManager(source));
        context.registerBean(AffectedRowsAspect.class);
        context.registerBean(
                TotpCredentialRepository.class,
                () -> new TotpCredentialRepository(template.getMapper(TotpCredentialMapper.class)));
        context.refresh();
        credentials = context.getBean(TotpCredentialRepository.class);
    }

    /** 释放测试事务上下文。 */
    @AfterAll
    static void close() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    @DisplayName("TOTP 时间步推进不更换凭据版本，旧版本不能替换或删除新密钥")
    void preservesStepAndCredentialVersionBoundaries() {
        var first = credentials
                .create(101L, new EncryptedSecret("test-key", new byte[] {1}), 100);
        assertThat(credentials.advanceAcceptedStep(101L, first.version(), 100)).isTrue();
        assertThat(credentials.advanceAcceptedStep(101L, first.version(), 99)).isFalse();
        assertThat(credentials.advanceAcceptedStep(101L, first.version(), 101)).isTrue();
        assertThat(credentials.findByAccountId(101L).version()).isEqualTo(first.version());
        var replacement = credentials.replace(
                101L,
                new EncryptedSecret("test-key-2", new byte[] {2}),
                102,
                first.version());
        assertThat(credentials.advanceAcceptedStep(101L, first.version(), 103)).isFalse();
        assertThatThrownBy(() -> credentials.delete(101L, first.version())).isInstanceOf(OptimisticLockException.class);
        assertThat(credentials.findByAccountId(101L).version()).isEqualTo(replacement.version());
        credentials.delete(101L, replacement.version());
        assertThat(credentials.findByAccountId(101L)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_totp_secret", Integer.class)).isZero();
        credentials.erase(101L);
        credentials.create(101L, new EncryptedSecret("test-key-3", new byte[] {3}), 104);
        credentials.erase(101L);
        assertThat(credentials.findByAccountId(101L)).isNull();
    }
}
