package com.sca4d.auth.x509;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.concurrent.exception.OptimisticLockException;
import com.sca4d.core.spring.persistence.mybatis.AffectedRowsAspect;
import com.sca4d.core.spring.persistence.mybatis.AffectedRowsInterceptor;
import com.sca4d.core.spring.persistence.mybatis.CoreEnumTypeHandler;
import com.sca4d.core.spring.persistence.mybatis.LongValueTypeHandler;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
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

import java.time.Instant;
import java.util.Objects;

/** 用隔离 PostgreSQL 验证凭据版本、状态及所属账号边界。 */
@Testcontainers
class X509CredentialPersistenceIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static AnnotationConfigApplicationContext context;

    private static X509CredentialRepository credentials;

    private static JdbcTemplate jdbc;

    /** 启用 Repository 的实际事务和影响行数检查。 */
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
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
                new ClassPathResource("META-INF/sca4dj/schema/auth-x509.sql"));
        schema.setSeparator(ScriptUtils.EOF_STATEMENT_SEPARATOR);
        schema.execute(source);
        jdbc = new JdbcTemplate(source);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(source);
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setDefaultEnumTypeHandler(CoreEnumTypeHandler.class);
        configuration.getTypeHandlerRegistry()
                .register(Version.class, JdbcType.BIGINT, new LongValueTypeHandler<>(Version::new, Version::value));
        factory.setConfiguration(configuration);
        factory.setPlugins(new AffectedRowsInterceptor());
        factory.setMapperLocations(new ClassPathResource("mapper/auth/X509CredentialMapper.xml"));
        SqlSessionTemplate template = new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        context = new AnnotationConfigApplicationContext();
        context.register(Transactions.class);
        context.registerBean(DataSourceTransactionManager.class, () -> new DataSourceTransactionManager(source));
        context.registerBean(AffectedRowsAspect.class);
        context.registerBean(
                X509CredentialRepository.class,
                () -> new X509CredentialRepository(template.getMapper(X509CredentialMapper.class)));
        context.refresh();
        credentials = context.getBean(X509CredentialRepository.class);
    }

    /** 释放测试事务上下文。 */
    @AfterAll
    static void close() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    @DisplayName("证书有效期边界、批量撤销及旧版本保护保持一致")
    void preservesValidityAndRevocationScope() {
        Instant now = Instant.parse("2026-09-15T00:00:00Z");
        X509Credential first = new X509Credential(
                1,
                101,
                "a".repeat(64),
                "one",
                "CN=test",
                now,
                now.plusSeconds(60),
                X509Credential.Status.ACTIVE,
                new Version(0),
                now);
        X509Credential second = new X509Credential(
                2,
                101,
                "b".repeat(64),
                "two",
                "CN=test",
                now,
                now.plusSeconds(60),
                X509Credential.Status.ACTIVE,
                new Version(0),
                now);
        X509Credential other = new X509Credential(
                3,
                102,
                "c".repeat(64),
                "three",
                "CN=test",
                now,
                now.plusSeconds(60),
                X509Credential.Status.ACTIVE,
                new Version(0),
                now);
        credentials.add(first, new byte[] {1});
        credentials.add(second, new byte[] {2});
        credentials.add(other, new byte[] {3});
        assertThat(credentials.findUsable(first.fingerprintSha256(), now)).isNotNull();
        assertThat(credentials.findUsable(first.fingerprintSha256(), now.plusSeconds(60))).isNull();
        assertThat(credentials.updateAllStatuses(101, X509Credential.Status.ACTIVE, X509Credential.Status.REVOKED, now))
                .isTrue();
        assertThat(credentials.hasUsable(101, now)).isFalse();
        assertThat(credentials.hasUsable(102, now)).isTrue();
        assertThatThrownBy(() -> credentials.updateStatus(first, X509Credential.Status.REVOKED, now))
                .isInstanceOf(OptimisticLockException.class);
        credentials.updateStatus(credentials.find(1), X509Credential.Status.ACTIVE, now);
        assertThat(
                jdbc.queryForObject("SELECT revoked_at IS NULL FROM auth_x509_credential WHERE id = 1", Boolean.class))
                .isTrue();
        credentials.erase(101);
        credentials.erase(101);
        assertThat(credentials.findAll(101)).isEmpty();
        assertThat(credentials.findAll(102)).hasSize(1);
    }
}
