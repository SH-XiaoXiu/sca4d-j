package com.sca4d.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.audit.config.AuditAutoConfiguration;
import com.sca4d.audit.query.AuditEntry;
import com.sca4d.audit.query.AuditQuery;
import com.sca4d.audit.query.AuditQueryService;
import com.sca4d.audit.recording.AfterCommitAuditRecorder;
import com.sca4d.audit.recording.AuditMessageHandler;
import com.sca4d.audit.recording.AuditMessageV2Codec;
import com.sca4d.audit.recording.AuditRepository;
import com.sca4d.audit.recording.QueuedAuditRecorder;
import com.sca4d.core.api.audit.AuditActor;
import com.sca4d.core.api.audit.AuditOperation;
import com.sca4d.core.api.audit.AuditPayload;
import com.sca4d.core.api.audit.AuditRecord;
import com.sca4d.core.api.audit.AuditRecorder;
import com.sca4d.core.api.audit.AuditResult;
import com.sca4d.core.api.audit.HttpCall;
import com.sca4d.core.api.call.SPICallValueResolver;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.messaging.DispatchReceipt;
import com.sca4d.core.api.messaging.IncomingMessage;
import com.sca4d.core.api.messaging.MessageBus;
import com.sca4d.core.api.name.Name;
import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.api.resource.Resource;
import com.sca4d.core.spring.call.SpelCallValueResolver;
import com.sca4d.core.spring.config.InstanceProperties;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.json.Sca4djMapper;
import com.sca4d.core.spring.persistence.mybatis.AffectedRowsAspect;
import com.sca4d.core.spring.persistence.mybatis.AffectedRowsInterceptor;
import com.sca4d.core.spring.persistence.mybatis.StringValueTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** 使用 PostgreSQL 17 验证审计追加、Raw JSON 和组合分页查询。 */
@Testcontainers
@SpringBootTest(classes = AuditPersistenceIntegrationTest.TestApplication.class, properties = {
        "spring.application.name=audit-test",
        "mybatis.mapper-locations=classpath*:mapper/audit/*.xml",
        "spring.autoconfigure.exclude=com.sca4d.core.spring.config.CoreSpringAutoConfiguration,"
                + "com.sca4d.core.spring.config.CoreJacksonAutoConfiguration,"
                + "com.sca4d.core.spring.openapi.CoreOpenApiConfiguration"
})
@SuppressWarnings({
        "SqlNoDataSourceInspection",
        "SpringJavaInjectionPointsAutowiringInspection"
}) // Testcontainers 和 SpringBootTest 在测试启动时提供数据源及测试 Bean，IDEA 无法静态解析。
class AuditPersistenceIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    @Qualifier("databaseAuditRecorder")
    private AuditRecorder recorder;

    @Autowired
    private AuditQueryService queries;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AuditRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.application.name", () -> "audit-test");
    }

    @BeforeEach
    void resetState() {
        jdbc.execute("DROP TABLE IF EXISTS audit_record_resource");
        jdbc.execute("DROP TABLE IF EXISTS audit_record");
        ResourceDatabasePopulator schema = new ResourceDatabasePopulator(
                new ClassPathResource("META-INF/sca4dj/schema/audit.sql"));
        schema.execute(Objects.requireNonNull(jdbc.getDataSource()));
        jdbc.execute("DROP TABLE IF EXISTS audit_handoff_test");
        jdbc.execute("CREATE TABLE audit_handoff_test (id BIGINT PRIMARY KEY)");
    }

    @Test
    void afterCommitPersistsFrozenAssociationsOnlyWhenCommitted() {
        var deferred = new AfterCommitAuditRecorder(repository, Runnable::run);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            deferred.record(record(31, "TEST/UPDATE", "101", AuditResult.SUCCESS, Instant.EPOCH));
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_record", Integer.class)).isZero();
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> deferred.record(record(31, "TEST/UPDATE", "101", AuditResult.SUCCESS, Instant.EPOCH)));
        var page = queries.page(
                new AuditQuery(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new PageRequest(1, 10),
                        "TEST/PROJECT",
                        "7"));
        assertThat(page.getTotal()).isEqualTo(1);
    }

    @Test
    void resourceAssociationsDeduplicateAndRollBackWithTheRecord() {
        var project = new Resource.Ref("TEST/PROJECT", "7");
        var group = new Resource.Ref("TEST/GROUP", "8");
        var row = new AuditRecord(
                "scope-call",
                new AuditActor(1, 2L, 3),
                new AuditOperation("TEST/MOVE"),
                group,
                AuditResult.SUCCESS,
                null,
                null,
                Instant.EPOCH,
                List.of(project, group, project));
        recorder.record(row);
        var query = new AuditQuery(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new PageRequest(1, 10),
                "TEST/PROJECT",
                "7").matchingResources(List.of(project, group));
        var page = queries.page(query);
        assertThat(page.getTotal()).isEqualTo(1);
        long id = page.getItems().getFirst().id();
        assertThat(queries.relatesTo(id, project)).isTrue();
        assertThat(queries.relatesTo(id, new Resource.Ref("TEST/PROJECT", "9"))).isFalse();
        assertThat(queries.page(query.matchingResources(List.of())).getTotal()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_record_resource", Integer.class)).isEqualTo(2);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            recorder.record(
                    new AuditRecord(
                            row.requestId(),
                            row.actor(),
                            row.operation(),
                            row.target().orElse(null),
                            row.result(),
                            row.payload().orElse(null),
                            row.httpCall().orElse(null),
                            row.occurredAt(),
                            row.relatedResources()));
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_record", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_record_resource", Integer.class)).isEqualTo(2);
    }

    @Test
    void recordsAndQueriesParentChildAuditTrail() {
        var actor = new AuditActor(1, 2L, 3);
        var root = new AuditRecord(
                "trail-request",
                "audit-root",
                "trail-1",
                null,
                actor,
                new AuditOperation("PROJECT/TEST/CREATE"),
                new Resource.Ref("TEST/PROJECT", "7"),
                AuditResult.SUCCESS,
                null,
                null,
                Instant.EPOCH,
                List.of(new Resource.Ref("TEST/PROJECT", "7")));
        var child = new AuditRecord(
                "trail-request",
                "audit-child",
                "trail-1",
                "audit-root",
                actor,
                new AuditOperation("TEST/TARGET/COMPLETE"),
                new Resource.Ref("TEST/LUMINAIRE", "9"),
                AuditResult.SUCCESS,
                null,
                null,
                Instant.EPOCH.plusSeconds(1),
                List.of(new Resource.Ref("TEST/PROJECT", "7")));

        recorder.record(root);
        recorder.record(child);

        assertThat(
                queries.page(
                        new AuditQuery(
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                new PageRequest(1, 10)).trail("trail-1"))
                        .getTotal())
                .isEqualTo(2);
        assertThat(
                queries.page(
                        new AuditQuery(
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                new PageRequest(1, 10)).parent("audit-root"))
                        .getTotal())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("追加 Raw JSON 后按 Principal、目标、结果和时间倒序分页")
    void appendsAndQueriesAuditRows() {
        recorder.record(
                record(
                        31L,
                        "STORAGE/FILE/WRITE",
                        "101",
                        AuditResult.SUCCESS,
                        Instant.parse("2026-07-28T08:00:00Z")));
        recorder.record(
                record(
                        31L,
                        "STORAGE/FILE/READ",
                        "102",
                        AuditResult.DENIED,
                        Instant.parse("2026-07-28T08:01:00Z")));
        recorder.record(
                record(
                        32L,
                        "STORAGE/FILE/READ",
                        "103",
                        AuditResult.SUCCESS,
                        Instant.parse("2026-07-28T08:02:00Z")));

        Page<AuditEntry> denied = queries.page(
                new AuditQuery(
                        "request-102",
                        21L,
                        31L,
                        "STORAGE/FILE/READ",
                        AuditResult.DENIED,
                        "STORAGE/FILE",
                        "102",
                        Instant.parse("2026-07-28T08:00:30Z"),
                        Instant.parse("2026-07-28T08:02:00Z"),
                        new PageRequest(1, 20)));
        Page<AuditEntry> firstPage = queries.page(
                new AuditQuery(
                        null,
                        null,
                        31L,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new PageRequest(1, 1)));
        Page<AuditEntry> secondPage = queries.page(
                new AuditQuery(
                        null,
                        null,
                        31L,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new PageRequest(2, 1)));

        assertThat(denied.getTotal()).isEqualTo(1);
        AuditEntry entry = denied.getItems().getFirst();
        assertThat(entry.operation().id()).isEqualTo("STORAGE/FILE/READ");
        assertThat(entry.target().orElseThrow().type()).isEqualTo(TreeCode.of("STORAGE/FILE"));
        assertThat(entry.payload().orElseThrow().type()).isEqualTo("raw");
        assertThat(entry.payload().orElseThrow().json()).containsSubsequence("\"reason\"", "\"test\"");
        assertThat(entry.payload().orElseThrow().decoded()).isEmpty();
        assertThat(entry.httpCall().orElseThrow().method()).isEqualTo("POST");
        assertThat(entry.httpCall().orElseThrow().path()).isEqualTo("/files/102");
        assertThat(entry.httpCall().orElseThrow().clientIp()).isEqualTo("127.0.0.1");
        assertThat(entry.httpCall().orElseThrow().userAgent()).contains("test client");
        assertThat(firstPage.getItems().getFirst().operation().id()).isEqualTo("STORAGE/FILE/READ");
        assertThat(secondPage.getItems().getFirst().operation().id()).isEqualTo("STORAGE/FILE/WRITE");
        assertThat(firstPage.getTotal()).isEqualTo(2);
    }

    @Test
    @DisplayName("队列交接拥有独立事务，原事务回滚仍保留已接收记录")
    void handsOffInAnIndependentTransaction() throws Exception {
        AtomicReference<com.sca4d.audit.recording.AuditMessage> captured = new AtomicReference<>();
        MessageBus bus = (message, policy) -> {
            captured.set((com.sca4d.audit.recording.AuditMessage) message);
            jdbc.update("INSERT INTO audit_handoff_test VALUES (42)");
            return new DispatchReceipt(Id.of(42), DispatchReceipt.Status.OUTBOX_ENLISTED);
        };
        AuditRecorder queued = queuedRecorder(bus);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            queued.record(record(31, "TEST/UPDATE", "101", AuditResult.SUCCESS, Instant.EPOCH));
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_handoff_test", Integer.class)).isEqualTo(1);
        AuditMessageHandler handler = new AuditMessageHandler(repository, new AuditMessageV2Codec(new ObjectMapper()));
        IncomingMessage<com.sca4d.audit.recording.AuditMessage> incoming = new IncomingMessage<>(
                Id.of(42),
                captured.get(),
                new Name("audit-test"),
                Instant.EPOCH,
                false,
                1);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> handler.handle(incoming));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> handler.handle(incoming));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_record WHERE id = 42", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_record_resource WHERE audit_id = 42", Integer.class))
                .isEqualTo(1);
        var codec = new AuditMessageV2Codec(new ObjectMapper());
        var altered = (ObjectNode) new ObjectMapper().readTree(codec.encode(captured.get()));
        altered.putArray("relatedResources").addObject().put("type", "TEST/PROJECT").put("id", "999");
        var replay = new IncomingMessage<>(
                Id.of(42),
                codec.decode(new ObjectMapper().writeValueAsBytes(altered)),
                new Name("audit-test"),
                Instant.EPOCH,
                true,
                2);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> handler.handle(replay));
        assertThat(queries.relatesTo(42, new Resource.Ref("TEST/PROJECT", "999"))).isFalse();

    }

    @Test
    @DisplayName("可靠交接失败回滚独立事务，不留假接收结果")
    void rollsBackFailedHandoff() {
        AuditRecorder queued = queuedRecorder((message, policy) -> {
            jdbc.update("INSERT INTO audit_handoff_test VALUES (42)");
            throw new IllegalStateException("handoff failed");
        });
        assertThatThrownBy(() -> queued.record(record(31, "TEST/UPDATE", "101", AuditResult.SUCCESS, Instant.EPOCH)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_handoff_test", Integer.class)).isZero();
    }

    /** 使用实际 Spring 事务代理验证 REQUIRES_NEW，不把普通对象调用冒充事务验证。 */
    private AuditRecorder queuedRecorder(MessageBus bus) {
        ProxyFactory proxy = new ProxyFactory(new QueuedAuditRecorder(repository, bus));
        TransactionInterceptor advice = new TransactionInterceptor();
        advice.setTransactionManager(transactionManager);
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        proxy.addAdvice(advice);
        return (AuditRecorder) proxy.getProxy();
    }

    private static AuditRecord record(
            long principalId,
            String operation,
            String fileId,
            AuditResult result,
            Instant occurredAt) {
        return new AuditRecord(
                "request-" + fileId,
                new AuditActor(11L, 21L, principalId),
                new AuditOperation(operation),
                new Resource.Ref("STORAGE/FILE", fileId),
                result,
                new RawPayload(Map.of("reason", "test")),
                new HttpCall("POST", "/files/" + fileId + "?token=secret", "127.0.0.1", "test\r\n client"),
                occurredAt,
                List.of(new Resource.Ref("TEST/PROJECT", "7")));
    }

    /** 保存不需要 App Codec 的测试 Raw JSON。 */
    @SuppressWarnings({"ClassCanBeRecord", "unused"}) // Jackson 通过反射读取 getter，项目禁止使用 Java record。
    private static class RawPayload implements AuditPayload {
        private final Map<String, String> values;

        private RawPayload(Map<String, String> values) {
            this.values = values;
        }

        /** @return 测试 payload 字段 */
        public Map<String, String> getValues() {
            return values;
        }
    }

    /** 为 Audit 自动配置提供测试 DataSource、ObjectMapper 和标识生成器。 */
    @SpringBootConfiguration
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class, MybatisAutoConfiguration.class,
            AuditAutoConfiguration.class})
    @EnableAspectJAutoProxy
    static class TestApplication {
        /** 与应用一致地检查 Repository 方法的影响行数声明，避免 @AffectedRows 违规只在应用里暴露。 */
        @Bean
        AffectedRowsAspect affectedRowsAspect() {
            return new AffectedRowsAspect();
        }

        @Bean
        AffectedRowsInterceptor affectedRowsInterceptor() {
            return new AffectedRowsInterceptor();
        }

        @Bean
        SPICallValueResolver expressionResolver(ApplicationContext context) {
            return new SpelCallValueResolver(context);
        }

        @Bean
        ConfigurationCustomizer resourceTypes() {
            return configuration -> configuration.getTypeHandlerRegistry()
                    .register(
                            TreeCode.class,
                            JdbcType.VARCHAR,
                            new StringValueTypeHandler<>(TreeCode::of, code -> code.format(NameFormat.SLASH)));
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        InstanceProperties instanceProperties() {
            return new InstanceProperties();
        }

        @Bean
        @Sca4djJson
        Sca4djMapper sca4djObjectMapper() {
            return new Sca4djMapper();
        }

        @Bean
        IdGenerator idGenerator() {
            AtomicLong ids = new AtomicLong(1000L);
            return () -> Id.of(ids.incrementAndGet());
        }
    }
}
