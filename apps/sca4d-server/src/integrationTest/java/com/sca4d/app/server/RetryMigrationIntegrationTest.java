package com.sca4d.app.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.retry.Backoff;
import com.sca4d.core.api.retry.RetryRule;
import com.sca4d.core.spring.json.Sca4djMapper;
import com.sca4d.core.spring.retry.RetryRuleCodec;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** 验证 V1 的重试进度和消息身份升级后不变，迁移失败可回滚后重新执行。 */
@Testcontainers
class RetryMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withCreateContainerCmdModifier(
                    command -> command.getHostConfig()
                            .withMemory(256L * 1024 * 1024)
                            .withNanoCPUs(500_000_000L));

    @Test
    void preservesActiveAttemptsAndMessageIdentityAfterRollback() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("1").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(source);
        Instant lease = Instant.parse("2026-10-12T10:00:00Z");
        long runId = Objects.requireNonNull(
                jdbc.queryForObject(
                        "INSERT INTO job.job_run (app_name, job_key, job_definition_version, job_kind, status) "
                                + "VALUES ('RETRY_TEST', 'TEST/RETRY', 1, 'DIRECT', 'RUNNING') RETURNING id",
                        Long.class));
        long nodeId = Objects.requireNonNull(
                jdbc.queryForObject(
                        "INSERT INTO job.job_run_node (run_id, node_key, node_kind, status, max_attempts, retry_delay_millis, attempt_count, available_at, lease_owner, lease_until) "
                                + "VALUES (?, 'direct', 'DIRECT', 'RUNNING', 3, 5000, 2, ?, 'worker', ?) RETURNING id",
                        Long.class,
                        runId,
                        Timestamp.from(lease.minusSeconds(60)),
                        Timestamp.from(lease)));
        jdbc.update(
                "INSERT INTO job.job_run_attempt (node_id, attempt_number, worker_id, status, started_at) "
                        + "VALUES (?, 2, 'worker', 'RUNNING', ?)",
                nodeId,
                Timestamp.from(lease.minusSeconds(60)));
        byte[] envelope = {1, 2, 3};
        jdbc.update(
                "INSERT INTO messaging_outbox (message_id, message_code, envelope, status, available_at, attempt_count, lease_id, lease_until) "
                        + "VALUES (901, 'TEST:RETRY', ?, 'SENDING', ?, 4, 'lease', ?)",
                envelope,
                Timestamp.from(lease.minusSeconds(60)),
                Timestamp.from(lease));

        Flyway upgraded = Flyway.configure().dataSource(source).locations("classpath:db/migration").target("2").load();
        // 同名索引使迁移在若干 ALTER 后失败，以检验 PostgreSQL 的事务回滚。
        jdbc.execute("CREATE INDEX uk_messaging_outbox_message ON messaging_outbox (message_code)");
        assertThatThrownBy(upgraded::migrate).isInstanceOf(FlywayException.class);
        assertThat(
                jdbc.queryForObject(
                        "SELECT retry_delay_millis FROM job.job_run_node WHERE id = ?",
                        Long.class,
                        nodeId))
                .isEqualTo(5000);
        assertThat(
                jdbc.queryForObject(
                        "SELECT count(*) FROM information_schema.columns "
                                + "WHERE table_name = 'messaging_outbox' AND column_name = 'outbox_id'",
                        Integer.class))
                .isZero();
        jdbc.execute("DROP INDEX uk_messaging_outbox_message");
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);

        RetryRuleCodec retryRules = new RetryRuleCodec(new Sca4djMapper());
        RetryRule restored = retryRules.decode(
                Objects.requireNonNull(
                        jdbc.queryForObject(
                                "SELECT retry_rule::text FROM job.job_run_node WHERE id = ?",
                                String.class,
                                nodeId)));
        assertThat(restored.maximumAttempts()).hasValue(3);
        assertThat(restored.backoff().delayAfter(2)).isEqualTo(Duration.ofSeconds(5));
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM job.job_run_node WHERE id = ?", Long.class, nodeId))
                .isEqualTo(2);
        assertThat(
                Objects.requireNonNull(
                        jdbc.queryForObject(
                                "SELECT lease_until FROM job.job_run_node WHERE id = ?",
                                Timestamp.class,
                                nodeId))
                        .toInstant())
                .isEqualTo(lease);
        assertThat(jdbc.queryForObject("SELECT outbox_id FROM messaging_outbox WHERE message_id = 901", Long.class))
                .isEqualTo(901);
        assertThat(jdbc.queryForObject("SELECT envelope FROM messaging_outbox WHERE message_id = 901", byte[].class))
                .containsExactly(envelope);
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM messaging_outbox WHERE message_id = 901", Long.class))
                .isEqualTo(4);

        String unlimited = retryRules.encode(RetryRule.unlimited(Backoff.fixed(Duration.ofSeconds(1))));
        jdbc.update("UPDATE job.job_run_node SET retry_rule = CAST(? AS JSONB) WHERE id = ?", unlimited, nodeId);
        assertThat(jdbc.queryForObject("SELECT max_attempts FROM job.job_run_node WHERE id = ?", Long.class, nodeId))
                .isNull();
        jdbc.update(
                "INSERT INTO messaging_outbox (outbox_id, message_id, message_code, envelope, status, available_at, "
                        + "target_handler_code, handler_attempt, handler_retry_rule) "
                        + "VALUES (902, 901, 'TEST:RETRY', ?, 'PENDING', ?, 'TEST:HANDLER', 2, CAST(? AS JSONB))",
                envelope,
                Timestamp.from(lease),
                unlimited);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM messaging_outbox WHERE message_id = 901", Integer.class))
                .isEqualTo(2);
    }
}
