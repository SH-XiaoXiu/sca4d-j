package com.sca4d.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.audit.query.AuditEntry;
import com.sca4d.audit.query.AuditQuery;
import com.sca4d.audit.query.AuditQueryService;
import com.sca4d.core.api.audit.AuditActor;
import com.sca4d.core.api.audit.AuditOperation;
import com.sca4d.core.api.audit.AuditPayload;
import com.sca4d.core.api.audit.AuditRecord;
import com.sca4d.core.api.audit.AuditRecorder;
import com.sca4d.core.api.audit.AuditResult;
import com.sca4d.core.api.audit.HttpCall;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.api.resource.ResourceReference;
import com.sca4d.core.spring.json.Sca4djJson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** 使用 PostgreSQL 17 验证审计追加、Raw JSON 和组合分页查询。 */
@Testcontainers
@SpringBootTest(classes = AuditPersistenceIntegrationTest.TestApplication.class, properties = "spring.autoconfigure.exclude="
        + "com.sca4d.core.spring.config.CoreSpringAutoConfiguration,"
        + "com.sca4d.core.spring.config.CoreJacksonAutoConfiguration,"
        + "com.sca4d.core.spring.openapi.CoreOpenApiConfiguration")
@SuppressWarnings({
        "SqlNoDataSourceInspection",
        "SpringJavaInjectionPointsAutowiringInspection"
}) // Testcontainers 和 SpringBootTest 在测试启动时提供数据源及测试 Bean，IDEA 无法静态解析。
class AuditPersistenceIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private AuditRecorder recorder;

    @Autowired
    private AuditQueryService queries;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.application.name", () -> "audit-test");
    }

    @BeforeEach
    void resetState() {
        jdbc.execute("DROP TABLE IF EXISTS audit_record");
        ResourceDatabasePopulator schema = new ResourceDatabasePopulator(
                new ClassPathResource("META-INF/sca4dj/schema/audit.sql"));
        schema.execute(Objects.requireNonNull(jdbc.getDataSource()));
    }

    @Test
    @DisplayName("追加 Raw JSON 后按 Principal、目标、结果和时间倒序分页")
    void appendsAndQueriesAuditRows() {
        recorder.record(
                record(
                        31L,
                        "storage/file/write",
                        "101",
                        AuditResult.SUCCESS,
                        Instant.parse("2026-07-28T08:00:00Z")));
        recorder.record(
                record(
                        31L,
                        "storage/file/read",
                        "102",
                        AuditResult.DENIED,
                        Instant.parse("2026-07-28T08:01:00Z")));
        recorder.record(
                record(
                        32L,
                        "storage/file/read",
                        "103",
                        AuditResult.SUCCESS,
                        Instant.parse("2026-07-28T08:02:00Z")));

        Page<AuditEntry> denied = queries.page(
                new AuditQuery(
                        "request-102",
                        21L,
                        31L,
                        "storage/file/read",
                        AuditResult.DENIED,
                        "file",
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
        assertThat(entry.operation().id()).isEqualTo("storage/file/read");
        assertThat(entry.target().orElseThrow().type()).isEqualTo("file");
        assertThat(entry.payload().orElseThrow().type()).isEqualTo("raw");
        assertThat(entry.payload().orElseThrow().json()).containsSubsequence("\"reason\"", "\"test\"");
        assertThat(entry.payload().orElseThrow().decoded()).isEmpty();
        assertThat(entry.httpCall().orElseThrow().method()).isEqualTo("POST");
        assertThat(entry.httpCall().orElseThrow().path()).isEqualTo("/files/102");
        assertThat(entry.httpCall().orElseThrow().clientIp()).isEqualTo("127.0.0.1");
        assertThat(entry.httpCall().orElseThrow().userAgent()).contains("test client");
        assertThat(firstPage.getItems().getFirst().operation().id()).isEqualTo("storage/file/read");
        assertThat(secondPage.getItems().getFirst().operation().id()).isEqualTo("storage/file/write");
        assertThat(firstPage.getTotal()).isEqualTo(2);
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
                new ResourceReference("file", fileId),
                result,
                new RawPayload(Map.of("reason", "test")),
                new HttpCall("POST", "/files/" + fileId + "?token=secret", "127.0.0.1", "test\r\n client"),
                occurredAt);
    }

    /** 保存不需要 App Codec 的测试 Raw JSON。 */
    @SuppressWarnings({"ClassCanBeRecord", "unused"}) // Jackson 通过反射读取 getter，项目禁止使用 Java record。
    private static final class RawPayload implements AuditPayload {
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
    @EnableAutoConfiguration
    static class TestApplication {
        @Bean
        @Sca4djJson
        ObjectMapper sca4djObjectMapper() {
            return new ObjectMapper();
        }

        @Bean
        IdGenerator idGenerator() {
            AtomicLong ids = new AtomicLong(1000L);
            return ids::incrementAndGet;
        }
    }
}
