package com.sca4d.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.concurrent.OptimisticLockException;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.isolation.AppName;
import com.sca4d.core.api.job.DirectJob;
import com.sca4d.core.api.job.Job;
import com.sca4d.core.api.job.JobExecution;
import com.sca4d.core.api.job.JobKey;
import com.sca4d.core.api.job.JobRetryPolicy;
import com.sca4d.core.api.job.JobRunFailedException;
import com.sca4d.core.api.job.JobRunStatus;
import com.sca4d.core.api.job.JobWaitTimeoutException;
import com.sca4d.core.api.job.ParallelResults;
import com.sca4d.core.api.job.PipelineJob;
import com.sca4d.core.api.job.PipelineJobPlan;
import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.pipeline.PipelineStage;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.api.scheduling.OverlapPolicy;
import com.sca4d.core.api.scheduling.ScheduleKey;
import com.sca4d.core.spring.persistence.batch.SqlBatchProperties;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.core.spring.persistence.mybatis.CoreEnumTypeHandler;
import com.sca4d.job.config.JobProperties;
import com.sca4d.job.persistence.JobMapper;
import com.sca4d.job.persistence.JobRepository;
import com.sca4d.job.persistence.NodeClaim;
import com.sca4d.job.runtime.DefaultJobExecutor;
import com.sca4d.job.runtime.DefaultJobHistory;
import com.sca4d.job.runtime.JobCatalog;
import com.sca4d.job.runtime.JobHistoryService;
import com.sca4d.job.runtime.JobRunService;
import com.sca4d.job.runtime.JobWorker;
import org.apache.ibatis.session.Configuration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import javax.sql.DataSource;

/** 使用真实 PostgreSQL 验证 Job 节点、重试、并发合并和计划 fire 约束。 */
@Testcontainers(disabledWithoutDocker = true)
class JobRuntimeIntegrationTest {

    private static final String APPLICATION_NAME = "JOB_TEST";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        AppName.publish(APPLICATION_NAME);
    }

    private DataSource dataSource;

    private JdbcTemplate jdbc;

    /** 每个用例重新创建 Job schema，避免运行状态互相影响。 */
    @BeforeEach
    void resetSchema() {
        dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP SCHEMA IF EXISTS job CASCADE");
        new ResourceDatabasePopulator(new ClassPathResource("META-INF/sca4dj/schema/job.sql"))
                .execute(dataSource);
    }

    /** DirectJob 首次失败后独立重试，并由同步调用读到同一持久化 run 的结果。 */
    @Test
    void directJobRetriesAndReturnsPersistedResult() {
        AtomicInteger attempts = new AtomicInteger();
        DirectJob<String, String> job = new DirectJob<>() {
            private final JobKey key = new JobKey("TEST/DIRECT/RETRY");

            @Override
            public @NotNull JobKey key() {
                return key;
            }

            @Override
            public int definitionVersion() {
                return 1;
            }

            @Override
            public @NotNull Class<String> inputType() {
                return String.class;
            }

            @Override
            public @NotNull Class<String> outputType() {
                return String.class;
            }

            @Override
            public @NotNull JobRetryPolicy retryPolicy() {
                return new JobRetryPolicy(2, Duration.ofMillis(10));
            }

            @Override
            protected @NotNull String execute(@NotNull JobExecution execution, @Nullable String input) {
                if (attempts.incrementAndGet() == 1) {
                    throw new IllegalStateException("first attempt");
                }
                return input + "-done";
            }
        };

        Runtime runtime = runtime(List.of(job));
        runtime.worker.start();
        try {
            assertThat(runtime.executor.run(job, "value", Duration.ofSeconds(10)))
                    .isEqualTo("value-done");
            assertThat(attempts).hasValue(2);
            assertThat(
                    jdbc.queryForObject(
                            "SELECT count(*) FROM job.job_run_attempt",
                            Integer.class))
                    .isEqualTo(2);
        } finally {
            runtime.worker.stop();
        }
    }

    /** 等待超时只结束当前调用，已经提交的 run 继续执行并可再次等待结果。 */
    @Test
    void waitTimeoutDoesNotCancelPersistedRun() {
        CountDownLatch release = new CountDownLatch(1);
        DirectJob<String, String> job = new DirectJob<>() {
            private final JobKey key = new JobKey("TEST/DIRECT/TIMEOUT");

            @Override
            public @NotNull JobKey key() {
                return key;
            }

            @Override
            public int definitionVersion() {
                return 1;
            }

            @Override
            public @NotNull Class<String> inputType() {
                return String.class;
            }

            @Override
            public @NotNull Class<String> outputType() {
                return String.class;
            }

            @Override
            public @NotNull JobRetryPolicy retryPolicy() {
                return JobRetryPolicy.once();
            }

            @Override
            protected @NotNull String execute(@NotNull JobExecution execution, @Nullable String input) {
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test release timeout");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("test interrupted", exception);
                }
                return input + "-done";
            }
        };

        Runtime runtime = runtime(List.of(job));
        runtime.worker.start();
        try {
            Id runId = runtime.executor.submit(job, "value");
            assertThatThrownBy(() -> runtime.executor.await(runId, job, Duration.ofMillis(100)))
                    .isInstanceOf(JobWaitTimeoutException.class)
                    .satisfies(
                            exception -> assertThat(((JobWaitTimeoutException) exception).runId())
                                    .isEqualTo(runId));

            release.countDown();
            assertThat(runtime.executor.await(runId, job, Duration.ofSeconds(10)))
                    .isEqualTo("value-done");
            assertThat(runtime.executor.status(runId)).isEqualTo(JobRunStatus.SUCCEEDED);
        } finally {
            release.countDown();
            runtime.worker.stop();
        }
    }

    /** Pipeline 节点重试耗尽后失败根 run，并取消依赖它且尚未开始的后继。 */
    @Test
    void pipelineFailureCancelsDependentNodes() {
        Pipeline<String, String> failing = Pipeline.of(new PipelineStage<>() {
            @Override
            public @NotNull String code() {
                return "failing";
            }

            @Override
            public @NotNull Class<String> inputType() {
                return String.class;
            }

            @Override
            public @NotNull Class<String> outputType() {
                return String.class;
            }

            @Override
            public @NotNull String execute(@NotNull String input) {
                throw new IllegalStateException("always fails");
            }
        });
        Pipeline<String, String> dependent = Pipeline.of(new PipelineStage<>() {
            @Override
            public @NotNull String code() {
                return "dependent";
            }

            @Override
            public @NotNull Class<String> inputType() {
                return String.class;
            }

            @Override
            public @NotNull Class<String> outputType() {
                return String.class;
            }

            @Override
            public @NotNull String execute(@NotNull String input) {
                return input;
            }
        });
        PipelineJob<String, String> job = new PipelineJob<>() {
            private final JobKey key = new JobKey("TEST/PIPELINE/FAILURE");

            @Override
            public @NotNull JobKey key() {
                return key;
            }

            @Override
            public int definitionVersion() {
                return 1;
            }

            @Override
            protected @NotNull PipelineJobPlan<String, String> plan() {
                return PipelineJobPlan.from(String.class)
                        .then("failing", failing, new JobRetryPolicy(2, Duration.ofMillis(10)))
                        .then("dependent", dependent, JobRetryPolicy.once());
            }
        };

        Runtime runtime = runtime(List.of(job));
        runtime.worker.start();
        try {
            Id runId = runtime.executor.submit(job, "value");
            assertThatThrownBy(() -> runtime.executor.await(runId, job, Duration.ofSeconds(10)))
                    .isInstanceOf(JobRunFailedException.class);
            assertThat(runtime.executor.status(runId)).isEqualTo(JobRunStatus.FAILED);
            assertThat(
                    jdbc.queryForObject(
                            "SELECT status FROM job.job_run_node WHERE run_id = ? AND node_key = 'failing'",
                            String.class,
                            runId.value()))
                    .isEqualTo("FAILED");
            assertThat(
                    jdbc.queryForObject(
                            "SELECT status FROM job.job_run_node WHERE run_id = ? AND node_key = 'dependent'",
                            String.class,
                            runId.value()))
                    .isEqualTo("CANCELLED");
            assertThat(
                    jdbc.queryForObject(
                            "SELECT count(*) FROM job.job_run_attempt WHERE node_id = "
                                    + "(SELECT id FROM job.job_run_node WHERE run_id = ? AND node_key = 'failing')",
                            Integer.class,
                            runId.value()))
                    .isEqualTo(2);
        } finally {
            runtime.worker.stop();
        }
    }

    /** PipelineJob 按顺序执行起点，并行执行两支，等二者成功后执行 merge。 */
    @Test
    void pipelineJobPersistsBranchesAndMerge() {
        Pipeline<String, String> normalize = Pipeline.of(new PipelineStage<>() {
            @Override
            public @NotNull String code() {
                return "normalize";
            }

            @Override
            public @NotNull Class<String> inputType() {
                return String.class;
            }

            @Override
            public @NotNull Class<String> outputType() {
                return String.class;
            }

            @Override
            public @NotNull String execute(@NotNull String input) {
                return input.trim();
            }
        });
        Pipeline<String, String> left = Pipeline.of(new PipelineStage<>() {
            @Override
            public @NotNull String code() {
                return "left";
            }

            @Override
            public @NotNull Class<String> inputType() {
                return String.class;
            }

            @Override
            public @NotNull Class<String> outputType() {
                return String.class;
            }

            @Override
            public @NotNull String execute(@NotNull String input) {
                return input.toUpperCase();
            }
        });
        Pipeline<String, Integer> right = Pipeline.of(new PipelineStage<>() {
            @Override
            public @NotNull String code() {
                return "right";
            }

            @Override
            public @NotNull Class<String> inputType() {
                return String.class;
            }

            @Override
            public @NotNull Class<Integer> outputType() {
                return Integer.class;
            }

            @Override
            public @NotNull Integer execute(@NotNull String input) {
                return input.length();
            }
        });
        Pipeline<ParallelResults<String, Integer>, String> merge = Pipeline.of(new PipelineStage<>() {
            @Override
            public @NotNull String code() {
                return "merge";
            }

            @Override
            public @NotNull Class<ParallelResults<String, Integer>> inputType() {
                return ParallelResults.type();
            }

            @Override
            public @NotNull Class<String> outputType() {
                return String.class;
            }

            @Override
            public @NotNull String execute(@NotNull ParallelResults<String, Integer> input) {
                return input.left() + ":" + input.right();
            }
        });
        PipelineJob<String, String> job = new PipelineJob<>() {
            private final JobKey key = new JobKey("TEST/PIPELINE/PARALLEL");

            @Override
            public @NotNull JobKey key() {
                return key;
            }

            @Override
            public int definitionVersion() {
                return 1;
            }

            @Override
            protected @NotNull PipelineJobPlan<String, String> plan() {
                return PipelineJobPlan.from(String.class)
                        .then("normalize", normalize, JobRetryPolicy.once())
                        .parallel(
                                "left",
                                left,
                                JobRetryPolicy.once(),
                                "right",
                                right,
                                JobRetryPolicy.once())
                        .merge("merge", merge, JobRetryPolicy.once());
            }
        };

        Runtime runtime = runtime(List.of(job));
        runtime.worker.start();
        try {
            assertThat(runtime.executor.run(job, " ab ", Duration.ofSeconds(10))).isEqualTo("AB:2");
            assertThat(
                    jdbc.queryForObject(
                            "SELECT count(*) FROM job.job_run_node WHERE status = 'SUCCEEDED'",
                            Integer.class))
                    .isEqualTo(4);
            assertThat(
                    jdbc.queryForObject(
                            "SELECT count(*) FROM job.job_run_dependency",
                            Integer.class))
                    .isEqualTo(4);
        } finally {
            runtime.worker.stop();
        }
    }

    /** 同一 fire 幂等返回原 run，活动 run 只在 SKIP_IF_RUNNING 下阻止新 fire。 */
    @Test
    void scheduledSubmissionDeduplicatesFireAndHonorsOverlapPolicy() {
        DirectJob<Void, Void> job = new DirectJob<>() {
            private final JobKey key = new JobKey("TEST/SCHEDULED");

            @Override
            public @NotNull JobKey key() {
                return key;
            }

            @Override
            public int definitionVersion() {
                return 1;
            }

            @Override
            public @NotNull Class<Void> inputType() {
                return Void.class;
            }

            @Override
            public @NotNull Class<Void> outputType() {
                return Void.class;
            }

            @Override
            public @NotNull JobRetryPolicy retryPolicy() {
                return JobRetryPolicy.once();
            }

            @Override
            protected @Nullable Void execute(@NotNull JobExecution execution, @Nullable Void input) {
                return null;
            }
        };
        Runtime runtime = runtime(List.of(job));
        ScheduleKey scheduleKey = new ScheduleKey("TEST/SCHEDULED");
        Instant first = Instant.parse("2026-07-30T00:00:00Z");
        Id firstRun = runtime.executor.submitScheduled(
                job,
                null,
                scheduleKey,
                first,
                OverlapPolicy.SKIP_IF_RUNNING).orElseThrow();
        assertThat(
                jdbc.queryForObject(
                        "SELECT runtime_namespace FROM job.job_run WHERE id = ?",
                        String.class,
                        firstRun.value()))
                .isEqualTo(APPLICATION_NAME);
        Optional<Id> duplicate = runtime.executor.submitScheduled(
                job,
                null,
                scheduleKey,
                first,
                OverlapPolicy.SKIP_IF_RUNNING);
        Optional<Id> skipped = runtime.executor.submitScheduled(
                job,
                null,
                scheduleKey,
                first.plusSeconds(1),
                OverlapPolicy.SKIP_IF_RUNNING);
        Optional<Id> allowed = runtime.executor.submitScheduled(
                job,
                null,
                scheduleKey,
                first.plusSeconds(2),
                OverlapPolicy.ALLOW);

        assertThat(duplicate).contains(firstRun);
        assertThat(skipped).isEmpty();
        assertThat(allowed).hasValueSatisfying(runId -> assertThat(runId).isNotEqualTo(firstRun));
    }

    /** 成功历史按默认 30 天窗口和批量删除，其他状态及其他 App 隔离名称保持不变。 */
    @Test
    void cleansOnlyExpiredSucceededRunsInConfiguredBatches() {
        Instant now = Instant.parse("2026-08-09T00:00:00Z");
        long firstExpired = insertRun(APPLICATION_NAME, "SUCCEEDED", now.minus(Duration.ofDays(40)));
        long secondExpired = insertRun(APPLICATION_NAME, "SUCCEEDED", now.minus(Duration.ofDays(35)));
        long recent = insertRun(APPLICATION_NAME, "SUCCEEDED", now.minus(Duration.ofDays(20)));
        long failed = insertRun(APPLICATION_NAME, "FAILED", now.minus(Duration.ofDays(60)));
        long queued = insertRun(APPLICATION_NAME, "QUEUED", null);
        long running = insertRun(APPLICATION_NAME, "RUNNING", null);
        long otherNamespace = insertRun("other-job-test", "SUCCEEDED", now.minus(Duration.ofDays(60)));

        long nodeId = Objects.requireNonNull(
                jdbc.queryForObject(
                        "INSERT INTO job.job_run_node (run_id, node_key, node_kind, status, parallel_merge, "
                                + "max_attempts, retry_delay_millis, attempt_count, available_at, completed_at) "
                                + "VALUES (?, 'direct', 'DIRECT', 'SUCCEEDED', 0, 1, 0, 1, ?, ?) RETURNING id",
                        Long.class,
                        firstExpired,
                        Timestamp.from(now.minus(Duration.ofDays(40))),
                        Timestamp.from(now.minus(Duration.ofDays(40)))));
        jdbc.update(
                "INSERT INTO job.job_run_attempt "
                        + "(node_id, attempt_number, worker_id, status, started_at, completed_at) "
                        + "VALUES (?, 1, 'test-worker', 'SUCCEEDED', ?, ?)",
                nodeId,
                Timestamp.from(now.minus(Duration.ofDays(40))),
                Timestamp.from(now.minus(Duration.ofDays(40))));

        JobProperties properties = new JobProperties();
        properties.setHistoryCleanupBatchSize(1);
        JobHistoryService history = new JobHistoryService(
                new JobRepository(mapper(), sqlBatches()),
                properties,
                Clock.fixed(now, ZoneOffset.UTC));
        var cleanupTransactions = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource));

        jdbc.execute(
                "CREATE FUNCTION job.reject_run_delete() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test rollback'; END $$");
        jdbc.execute(
                "CREATE TRIGGER reject_run_delete BEFORE DELETE ON job.job_run FOR EACH ROW EXECUTE FUNCTION job.reject_run_delete()");
        try {
            assertThatThrownBy(() -> cleanupTransactions.execute(status -> history.cleanupSucceededRuns()))
                    .isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM job.job_run_node WHERE id = ?", Integer.class, nodeId))
                    .isEqualTo(1);
            assertThat(
                    jdbc.queryForObject(
                            "SELECT count(*) FROM job.job_run_attempt WHERE node_id = ?",
                            Integer.class,
                            nodeId))
                    .isEqualTo(1);
        } finally {
            jdbc.execute("DROP TRIGGER reject_run_delete ON job.job_run");
        }

        assertThat(cleanupTransactions.<Integer>execute(status -> history.cleanupSucceededRuns())).isEqualTo(1);
        assertThat(
                jdbc.queryForObject(
                        "SELECT count(*) FROM job.job_run WHERE id = ?",
                        Integer.class,
                        firstExpired))
                .isZero();
        assertThat(
                jdbc.queryForObject(
                        "SELECT count(*) FROM job.job_run_node WHERE id = ?",
                        Integer.class,
                        nodeId))
                .isZero();
        assertThat(
                jdbc.queryForObject(
                        "SELECT count(*) FROM job.job_run_attempt WHERE node_id = ?",
                        Integer.class,
                        nodeId))
                .isZero();

        assertThat(cleanupTransactions.<Integer>execute(status -> history.cleanupSucceededRuns())).isEqualTo(1);
        assertThat(cleanupTransactions.<Integer>execute(status -> history.cleanupSucceededRuns())).isZero();
        assertThat(
                jdbc.queryForList(
                        "SELECT id FROM job.job_run ORDER BY id",
                        Long.class))
                .containsExactly(recent, failed, queued, running, otherNamespace)
                .doesNotContain(secondExpired);
    }

    /** 调度历史按应用、Job 定义和计划隔离，并把成功节点输出恢复为声明类型。 */
    @Test
    void findsTypedScheduledNodeOutputsWithoutRepositoryPageLimit() {
        PipelineJob<Integer, String> job = new PipelineJob<>() {
            private final PipelineJobPlan<Integer, String> definition = PipelineJobPlan.from(Integer.class)
                    .then(
                            "outcome",
                            Pipeline.of(
                                    PipelineStage.of(
                                            "to-text",
                                            Integer.class,
                                            String.class,
                                            Object::toString)),
                            JobRetryPolicy.once());

            @Override
            public @NotNull JobKey key() {
                return new JobKey("TEST/SCHEDULED_HISTORY");
            }

            @Override
            public int definitionVersion() {
                return 2;
            }

            @Override
            protected @NotNull PipelineJobPlan<Integer, String> plan() {
                return definition;
            }
        };
        ScheduleKey scheduleKey = new ScheduleKey("TEST/HISTORY/SCHEDULE");
        Instant first = Instant.parse("2026-09-05T01:00:00Z");
        Instant second = first.plusSeconds(60);
        long failed = insertScheduledRun(
                APPLICATION_NAME,
                job.key().format(NameFormat.SLASH),
                job.definitionVersion(),
                scheduleKey.format(NameFormat.SLASH),
                "FAILED",
                first);
        insertOutcome(failed, "\"business-rejected\"", first);
        long succeeded = insertScheduledRun(
                APPLICATION_NAME,
                job.key().format(NameFormat.SLASH),
                job.definitionVersion(),
                scheduleKey.format(NameFormat.SLASH),
                "SUCCEEDED",
                second);
        insertOutcome(succeeded, "\"created\"", second);
        insertScheduledRun(
                "other-application",
                job.key().format(NameFormat.SLASH),
                job.definitionVersion(),
                scheduleKey.format(NameFormat.SLASH),
                "SUCCEEDED",
                second.plusSeconds(60));
        insertScheduledRun(
                APPLICATION_NAME,
                job.key().format(NameFormat.SLASH),
                1,
                scheduleKey.format(NameFormat.SLASH),
                "SUCCEEDED",
                second.plusSeconds(120));

        DefaultJobHistory history = new DefaultJobHistory(
                new JobRepository(mapper(), sqlBatches()),
                new ObjectMapper().findAndRegisterModules());
        var firstPage = history.findScheduledRuns(
                scheduleKey,
                job.nodeOutput("outcome", String.class),
                new PageRequest(1, 1));
        var secondPage = history.findScheduledRuns(
                scheduleKey,
                job.nodeOutput("outcome", String.class),
                new PageRequest(2, 1));

        assertThat(firstPage.getTotal()).isEqualTo(2);
        assertThat(firstPage.getItems()).singleElement().satisfies(run -> {
            assertThat(run.runId()).isEqualTo(Id.of(succeeded));
            assertThat(run.status()).isEqualTo(JobRunStatus.SUCCEEDED);
            assertThat(run.output()).isEqualTo("created");
        });
        assertThat(secondPage.getItems()).singleElement().satisfies(run -> {
            assertThat(run.runId()).isEqualTo(Id.of(failed));
            assertThat(run.status()).isEqualTo(JobRunStatus.FAILED);
            assertThat(run.output()).isEqualTo("business-rejected");
        });
        assertThat(
                history.findScheduledRuns(
                        scheduleKey,
                        job.nodeOutput("outcome", String.class),
                        new PageRequest(1, 250))
                        .getItems())
                .hasSize(2);
    }

    /** 插入一条具有明确计划、Job 定义和完成时间的根运行。 */
    private long insertScheduledRun(
            @NotNull String namespace,
            @NotNull String jobKey,
            int definitionVersion,
            @NotNull String scheduleKey,
            @NotNull String status,
            @NotNull Instant scheduledAt) {
        return Objects.requireNonNull(
                jdbc.queryForObject(
                        "INSERT INTO job.job_run "
                                + "(runtime_namespace, job_key, job_definition_version, job_kind, status, "
                                + "schedule_key, scheduled_fire_time, created_at, completed_at) "
                                + "VALUES (?, ?, ?, 'PIPELINE', ?, ?, ?, ?, ?) RETURNING id",
                        Long.class,
                        namespace,
                        jobKey,
                        definitionVersion,
                        status,
                        scheduleKey,
                        Timestamp.from(scheduledAt),
                        Timestamp.from(scheduledAt.minusSeconds(1)),
                        Timestamp.from(scheduledAt)));
    }

    /** 为指定根运行保存一个已经成功的 Pipeline 节点输出。 */
    private void insertOutcome(
            long runId,
            @NotNull String outputJson,
            @NotNull Instant completedAt) {
        jdbc.update(
                "INSERT INTO job.job_run_node "
                        + "(run_id, node_key, node_kind, output_json, status, parallel_merge, max_attempts, "
                        + "retry_delay_millis, attempt_count, available_at, completed_at) "
                        + "VALUES (?, ?, 'PIPELINE', CAST(? AS JSONB), 'SUCCEEDED', 0, 1, 0, 1, ?, ?)",
                runId,
                "outcome",
                outputJson,
                Timestamp.from(completedAt),
                Timestamp.from(completedAt));
    }

    /** 旧工作实例和旧尝试不能写回，成功的 Void 输出同时完成节点和本次尝试。 */
    @Test
    void rejectsStaleAttemptResultsAndClearsVoidOutput() {
        Instant now = Instant.parse("2026-09-15T00:00:00Z");
        long runId = insertRun(APPLICATION_NAME, "RUNNING", null);
        long nodeId = Objects.requireNonNull(
                jdbc.queryForObject(
                        "INSERT INTO job.job_run_node (run_id, node_key, node_kind, status, max_attempts, retry_delay_millis, attempt_count, available_at, lease_owner, lease_until, output_json) VALUES (?, 'direct', 'DIRECT', 'RUNNING', 3, 0, 2, ?, 'new', ?, '\"old\"'::jsonb) RETURNING id",
                        Long.class,
                        runId,
                        Timestamp.from(now),
                        Timestamp.from(now.plusSeconds(60))));
        jdbc.update(
                "INSERT INTO job.job_run_attempt (node_id, attempt_number, worker_id, status, started_at) VALUES (?, 2, 'new', 'RUNNING', ?)",
                nodeId,
                Timestamp.from(now));
        JobRepository repository = new JobRepository(mapper(), sqlBatches());
        var previous = new NodeClaim(
                nodeId,
                runId,
                new JobKey("TEST"),
                1,
                "DIRECT",
                "direct",
                null,
                1,
                3,
                0);
        var current = new NodeClaim(
                nodeId,
                runId,
                new JobKey("TEST"),
                1,
                "DIRECT",
                "direct",
                null,
                2,
                3,
                0);
        var transactions = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource));
        assertThatThrownBy(
                () -> transactions.executeWithoutResult(
                        status -> repository.saveNodeResult(previous.succeeded(null, now), "new")))
                .isInstanceOf(OptimisticLockException.class);
        assertThatThrownBy(
                () -> transactions
                        .executeWithoutResult(status -> repository.saveNodeResult(current.succeeded(null, now), "old")))
                .isInstanceOf(OptimisticLockException.class);
        transactions.executeWithoutResult(status -> repository.saveNodeResult(current.succeeded(null, now), "new"));
        assertThat(
                jdbc.queryForObject(
                        "SELECT status = 'SUCCEEDED' AND output_json IS NULL AND lease_owner IS NULL FROM job.job_run_node WHERE id = ?",
                        Boolean.class,
                        nodeId))
                .isTrue();
        assertThat(
                jdbc.queryForObject(
                        "SELECT status FROM job.job_run_attempt WHERE node_id = ? AND attempt_number = 2",
                        String.class,
                        nodeId))
                .isEqualTo("SUCCEEDED");
    }

    /** 插入一个用于历史保留测试的根运行。 */
    private long insertRun(@NotNull String namespace, @NotNull String status, @Nullable Instant completedAt) {
        Instant createdAt = completedAt == null
                ? Instant.parse("2026-08-09T00:00:00Z")
                : completedAt.minusSeconds(1);
        return Objects.requireNonNull(
                jdbc.queryForObject(
                        "INSERT INTO job.job_run "
                                + "(runtime_namespace, job_key, job_definition_version, job_kind, status, created_at, completed_at) "
                                + "VALUES (?, 'TEST/HISTORY', 1, 'DIRECT', ?, ?, ?) RETURNING id",
                        Long.class,
                        namespace,
                        status,
                        Timestamp.from(createdAt),
                        completedAt == null ? null : Timestamp.from(completedAt)));
    }

    /** 创建使用同一真实 DataSource 的 Repository、Executor 和 worker。 */
    private Runtime runtime(List<? extends Job<?, ?>> jobs) {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JobCatalog catalog = new JobCatalog(List.copyOf(jobs));
        JobMapper mapper = mapper();
        JobRepository repository = new JobRepository(mapper, sqlBatches());
        JobRunService runs = new JobRunService(
                repository,
                new DataSourceTransactionManager(dataSource),
                Clock.systemUTC(),
                immediateLock());
        DefaultJobExecutor executor = new DefaultJobExecutor(catalog, runs, objectMapper);
        JobProperties properties = new JobProperties();
        properties.setWorkerCount(4);
        properties.setClaimBatchSize(4);
        properties.setPollInterval(Duration.ofMillis(10));
        properties.setLeaseDuration(Duration.ofSeconds(5));
        properties.setLeaseRenewInterval(Duration.ofSeconds(1));
        properties.validate();
        JobWorker worker = new JobWorker(
                catalog,
                runs,
                objectMapper,
                properties,
                Clock.systemUTC());
        return new Runtime(executor, worker);
    }

    /** @return 立即执行任务的单进程测试锁 */
    private static @NotNull DistributedLock immediateLock() {
        return new DistributedLock() {
            @Override
            public <T> T execute(LockKey key, Duration waitTime, Supplier<T> task) {
                return task.get();
            }
        };
    }

    /** 使用 Job 模块的 MyBatis XML 创建与真实测试 DataSource 绑定的 Mapper。 */
    @SuppressWarnings("resource") // Mapper 代理在用例期间持续使用同一个 SqlSessionTemplate。
    private JobMapper mapper() {
        try {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            Configuration configuration = new Configuration();
            configuration.setDefaultEnumTypeHandler(CoreEnumTypeHandler.class);
            factory.setConfiguration(configuration);
            factory.setMapperLocations(new ClassPathResource("mapper/job/JobMapper.xml"));
            factory.afterPropertiesSet();
            return new SqlSessionTemplate(Objects.requireNonNull(factory.getObject(), "SqlSessionFactory 不能为空"))
                    .getMapper(JobMapper.class);
        } catch (Exception exception) {
            throw new IllegalStateException("创建 Job MyBatis Mapper 失败", exception);
        }
    }

    /** 创建使用默认保护上限的 SQL 批处理器。 */
    private static SqlBatches sqlBatches() {
        return new SqlBatches(new SqlBatchProperties());
    }

    /** 保存一个测试用 Executor 和对应 worker。 */
    @SuppressWarnings("ClassCanBeRecord") // 测试类型遵守项目禁用 record 的规则。
    private static class Runtime {
        private final DefaultJobExecutor executor;

        private final JobWorker worker;

        private Runtime(DefaultJobExecutor executor, JobWorker worker) {
            this.executor = executor;
            this.worker = worker;
        }
    }
}
