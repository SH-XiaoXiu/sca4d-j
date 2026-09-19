package com.sca4d.openapi.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.auth.hmac.HmacCredential;
import com.sca4d.auth.hmac.HmacCredentialMapper;
import com.sca4d.auth.hmac.HmacCredentialRepository;
import com.sca4d.auth.sharedsecret.SharedSecretCredential;
import com.sca4d.auth.sharedsecret.SharedSecretCredentialMapper;
import com.sca4d.auth.sharedsecret.SharedSecretCredentialRepository;
import com.sca4d.core.api.concurrent.OptimisticLockException;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.spring.persistence.batch.SqlBatchProperties;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.core.spring.persistence.mybatis.AffectedRowsAspect;
import com.sca4d.core.spring.persistence.mybatis.AffectedRowsInterceptor;
import com.sca4d.core.spring.persistence.mybatis.CoreEnumTypeHandler;
import com.sca4d.core.spring.persistence.mybatis.LongValueTypeHandler;
import com.sca4d.openapi.client.mapper.OpenApiClientMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import javax.sql.DataSource;

/** 在隔离 PostgreSQL 中验证 Client 授权关系的原子替换与并发隔离。 */
@Testcontainers
class OpenApiClientPersistenceIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static AnnotationConfigApplicationContext context;

    private static OpenApiClientRepository clients;

    private static JdbcTemplate jdbc;

    private static TransactionTemplate transactions;

    /** 使用与生产相同的 Mapper 扫描和 Spring 事务代理。 */
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @MapperScan(basePackageClasses = OpenApiClientMapper.class)
    static class PersistenceConfiguration {
    }

    /** 加载正式表结构，独立配置数据库及枚举映射。 */
    @BeforeAll
    static void initialize() throws Exception {
        DataSource source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        ResourceDatabasePopulator schema = new ResourceDatabasePopulator(
                new ClassPathResource("META-INF/sca4dj/schema/dictionary-settings.sql"),
                new ClassPathResource("META-INF/sca4dj/schema/auth-hmac.sql"),
                new ClassPathResource("META-INF/sca4dj/schema/auth-shared-secret.sql"),
                new ClassPathResource("META-INF/sca4dj/schema/openapi.sql"));
        schema.setSeparator(org.springframework.jdbc.datasource.init.ScriptUtils.EOF_STATEMENT_SEPARATOR);
        schema.execute(source);
        jdbc = new JdbcTemplate(source);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(source);
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setDefaultEnumTypeHandler(CoreEnumTypeHandler.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.getTypeHandlerRegistry()
                .register(
                        Version.class,
                        org.apache.ibatis.type.JdbcType.BIGINT,
                        new LongValueTypeHandler<>(Version::new, Version::value));
        factory.setConfiguration(configuration);
        factory.setPlugins(new AffectedRowsInterceptor());
        factory.setMapperLocations(
                new ClassPathResource("mapper/openapi/OpenApiClientMapper.xml"),
                new ClassPathResource("mapper/auth/HmacCredentialMapper.xml"),
                new ClassPathResource("mapper/auth/SharedSecretCredentialMapper.xml"));
        SqlSessionFactory sessions = factory.getObject();
        DataSourceTransactionManager manager = new DataSourceTransactionManager(source);
        transactions = new TransactionTemplate(manager);
        context = new AnnotationConfigApplicationContext();
        context.register(PersistenceConfiguration.class);
        context.registerBean(SqlSessionFactory.class, () -> sessions);
        context.registerBean(DataSourceTransactionManager.class, () -> manager);
        context.registerBean(SqlBatches.class, () -> new SqlBatches(new SqlBatchProperties()));
        context.registerBean(OpenApiClientRepository.class);
        org.mybatis.spring.SqlSessionTemplate template = new org.mybatis.spring.SqlSessionTemplate(sessions);
        context.registerBean(HmacCredentialMapper.class, () -> template.getMapper(HmacCredentialMapper.class));
        context.registerBean(
                SharedSecretCredentialMapper.class,
                () -> template.getMapper(SharedSecretCredentialMapper.class));
        context.registerBean(HmacCredentialRepository.class);
        context.registerBean(SharedSecretCredentialRepository.class);
        context.registerBean(AffectedRowsAspect.class);
        context.refresh();
        clients = context.getBean(OpenApiClientRepository.class);
    }

    /** 释放测试事务上下文。 */
    @AfterAll
    static void close() {
        if (context != null) {
            context.close();
        }
    }

    /** 每个用例使用独立的 Client 集合。 */
    @BeforeEach
    void clear() {
        jdbc.execute(
                "TRUNCATE openapi_client_operation, openapi_client, auth_hmac_credential, auth_shared_secret_credential");
    }

    @Test
    @DisplayName("Client HMAC 撤销保留密文、重签清除撤销时间且旧版本不能覆盖新凭据")
    void hmacLifecyclePreservesCredentialVersion() {
        HmacCredentialRepository credentials = context.getBean(HmacCredentialRepository.class);
        Instant now = Instant.parse("2026-09-15T00:00:00Z");
        HmacCredential initial = credentials.create(101, new EncryptedSecret("test-key", new byte[] {1}));
        assertThat(credentials.updateStatus(101, HmacCredential.Status.ACTIVE, HmacCredential.Status.REVOKED, now))
                .isTrue();
        assertThat(credentials.find(101).encryptedKey()).isEqualTo(initial.encryptedKey());
        assertThat(
                jdbc.queryForObject(
                        "SELECT revoked_at IS NOT NULL FROM auth_hmac_credential WHERE account_id = 101",
                        Boolean.class))
                .isTrue();
        assertThat(credentials.updateStatus(101, HmacCredential.Status.ACTIVE, HmacCredential.Status.REVOKED, now))
                .isFalse();
        HmacCredential saved = credentials.replace(
                credentials.find(101),
                new EncryptedSecret("test-key-2", new byte[] {2}),
                HmacCredential.Status.ACTIVE,
                now);
        assertThat(saved.active()).isTrue();
        assertThat(
                jdbc.queryForObject(
                        "SELECT revoked_at IS NULL FROM auth_hmac_credential WHERE account_id = 101",
                        Boolean.class))
                .isTrue();
        assertThatThrownBy(
                () -> credentials.replace(initial, initial.encryptedKey(), HmacCredential.Status.ACTIVE, now))
                .isInstanceOf(OptimisticLockException.class);
        assertThat(credentials.find(101).encryptedKey()).isEqualTo(saved.encryptedKey());
    }

    @Test
    @DisplayName("Client Shared Secret 撤销保留摘要、重签替换摘要并拒绝旧版本")
    void sharedSecretLifecyclePreservesCredentialVersion() {
        SharedSecretCredentialRepository credentials = context.getBean(SharedSecretCredentialRepository.class);
        Instant now = Instant.parse("2026-09-15T00:00:00Z");
        SharedSecretCredential initial = credentials.create(101, "test-hash");
        assertThat(
                credentials.updateStatus(
                        101,
                        SharedSecretCredential.Status.ACTIVE,
                        SharedSecretCredential.Status.REVOKED,
                        now))
                .isTrue();
        assertThat(credentials.find(101).secretHash()).isEqualTo("test-hash");
        SharedSecretCredential saved = credentials
                .replace(credentials.find(101), "test-hash-2", SharedSecretCredential.Status.ACTIVE, now);
        assertThat(saved.active()).isTrue();
        assertThat(
                jdbc.queryForObject(
                        "SELECT revoked_at IS NULL FROM auth_shared_secret_credential WHERE account_id = 101",
                        Boolean.class))
                .isTrue();
        assertThatThrownBy(() -> credentials.replace(initial, "test-hash-3", SharedSecretCredential.Status.ACTIVE, now))
                .isInstanceOf(OptimisticLockException.class);
        assertThat(credentials.find(101).secretHash()).isEqualTo("test-hash-2");
    }

    @Test
    @DisplayName("关系插入失败回滚新 Client，替换失败保留原授权，空集合明确清空")
    void aggregateWritesAreAtomic() {
        OpenApiClient client = new OpenApiClient("client-a", 101, "测试 Client", OpenApiClientStatus.ACTIVE);
        assertThatThrownBy(() -> clients.create(client, List.of("read", "read"))).isInstanceOf(RuntimeException.class);
        assertThat(clients.find("client-a")).isEmpty();
        clients.create(client, List.of("read"));
        assertThatThrownBy(() -> clients.replaceOperations("client-a", List.of("write", "write")))
                .isInstanceOf(RuntimeException.class);
        assertThat(clients.findOperations("client-a")).containsExactly("read");
        assertThat(clients.findByAccountId(101).orElseThrow().clientId()).isEqualTo("client-a");
        clients.updateStatus("client-a", OpenApiClientStatus.DISABLED);
        assertThat(clients.find("client-a").orElseThrow().active()).isFalse();
        clients.replaceOperations("client-a", List.of());
        assertThat(clients.hasOperation("client-a", "read")).isFalse();
        assertThatThrownBy(() -> clients.replaceOperations("missing", List.of("read")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(clients.findOperations("missing")).isEmpty();
    }

    @Test
    @DisplayName("超过一个批次的完整授权保存及跨批次失败整体回滚")
    void batchesPreserveTheCompleteSelection() {
        List<String> operations = IntStream.range(0, 1001).mapToObj(i -> "operation-" + i).toList();
        clients.create(new OpenApiClient("client-b", 102, "批量 Client", OpenApiClientStatus.ACTIVE), operations);
        assertThat(clients.findOperations("client-b")).containsExactlyInAnyOrderElementsOf(operations);
        List<String> invalid = new java.util.ArrayList<>(operations.subList(0, 1000));
        invalid.add(operations.getFirst());
        assertThatThrownBy(() -> clients.replaceOperations("client-b", invalid)).isInstanceOf(RuntimeException.class);
        assertThat(clients.findOperations("client-b")).containsExactlyInAnyOrderElementsOf(operations);
    }

    @Test
    @DisplayName("并发完整替换不会把两次选择合并为额外授权")
    void concurrentReplacementsKeepOneCompleteSelection() throws Exception {
        clients.create(new OpenApiClient("client-c", 103, "并发 Client", OpenApiClientStatus.ACTIVE), List.of());
        CountDownLatch firstWritten = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transactions.executeWithoutResult(status -> {
                clients.replaceOperations("client-c", List.of("read"));
                firstWritten.countDown();
                try {
                    assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }));
            var second = executor.submit(() -> {
                assertThat(firstWritten.await(10, TimeUnit.SECONDS)).isTrue();
                secondStarted.countDown();
                clients.replaceOperations("client-c", List.of("write"));
                return null;
            });
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        }
        assertThat(clients.findOperations("client-c")).containsExactly("write");
    }
}
