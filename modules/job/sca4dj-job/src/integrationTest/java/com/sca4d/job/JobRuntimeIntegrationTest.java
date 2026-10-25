package com.sca4d.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.concurrent.exception.OptimisticLockException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.identity.SystemPrincipal;
import com.sca4d.core.api.identity.SystemPrincipals;
import com.sca4d.core.api.isolation.AppName;
import com.sca4d.core.api.job.DirectJob;
import com.sca4d.core.api.job.Job;
import com.sca4d.core.api.job.JobExecution;
import com.sca4d.core.api.job.JobKey;
import com.sca4d.core.api.job.JobRunRecording;
import com.sca4d.core.api.job.JobRunStatus;
import com.sca4d.core.api.job.JobSubmission;
import com.sca4d.core.api.job.JobTarget;
import com.sca4d.core.api.job.JobWorkKey;
import com.sca4d.core.api.job.ParallelResults;
import com.sca4d.core.api.job.PipelineJob;
import com.sca4d.core.api.job.PipelineJobPlan;
import com.sca4d.core.api.job.exception.JobRunFailedException;
import com.sca4d.core.api.job.exception.JobWaitTimeoutException;
import com.sca4d.core.api.name.Name;
import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.pipeline.PipelineStage;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.api.retry.Backoff;
import com.sca4d.core.api.retry.RetryRule;
import com.sca4d.core.api.runtime.InstanceCapabilities;
import com.sca4d.core.api.runtime.InstanceCapability;
import com.sca4d.core.api.runtime.InstanceId;
import com.sca4d.core.api.scheduling.OverlapPolicy;
import com.sca4d.core.api.scheduling.ScheduleKey;
import com.sca4d.core.spring.call.CallHolder;
import com.sca4d.core.spring.config.InstanceProperties;
import com.sca4d.core.spring.json.Sca4djMapper;
import com.sca4d.core.spring.persistence.batch.SqlBatchProperties;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.core.spring.persistence.mybatis.CoreEnumTypeHandler;
import com.sca4d.core.spring.persistence.mybatis.StringValueTypeHandler;
import com.sca4d.core.spring.persistence.mybatis.TreeCodeTypeHandler;
import com.sca4d.core.spring.retry.RetryRuleCodec;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
        SystemPrincipals.publish(
                Map.of(
                        SystemPrincipals.MAIN,
                        new SystemPrincipal(1, SystemPrincipals.MAIN)));
    }

    private DataSource dataSource;

    private final RetryRuleCodec retryRules = new RetryRuleCodec(new Sca4djMapper());

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
            public @NotNull RetryRule retryRule() {
                return RetryRule.atMost(2, Backoff.fixed(Duration.ofMillis(10)));
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
            public @NotNull RetryRule retryRule() {
                return RetryRule.once();
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
            Id runId = runtime.executor.submit(job, "value").runId().orElseThrow();
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
                        .then("failing", failing, RetryRule.atMost(2, Backoff.fixed(Duration.ofMillis(10))))
                        .then("dependent", dependent, RetryRule.once());
            }
        };

        Runtime runtime = runtime(List.of(job));
        runtime.worker.start();
        try {
            Id runId = runtime.executor.submit(job, "value").runId().orElseThrow();
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
        CountDownLatch bothBranches = new CountDownLatch(2);
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
                awaitBothBranches(bothBranches);
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
                awaitBothBranches(bothBranches);
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
                        .then("normalize", normalize, RetryRule.once())
                        .parallel(
                                "left",
                                left,
                                RetryRule.once(),
                                "right",
                                right,
                                RetryRule.once())
                        .merge("merge", merge, RetryRule.once());
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

    /** 同键 WAIT 创建两个 Run，后一个只在前一个成为终态后可领取。 */
    @Test
    void waitsForEarlierWorkBeforeClaimingTheNextRun() {
        DirectJob<Long, Void> job = workJob(JobTarget.any(), OverlapPolicy.WAIT, JobRunRecording.PERSISTED);
        Runtime runtime = runtime(List.of(job));
        Id first = runtime.executor.submit(job, 42L).runId().orElseThrow();
        Id second = runtime.executor.submit(job, 42L).runId().orElseThrow();
        assertThat(second).isNotEqualTo(first);

        var firstClaim = runtime.runs.claim(
                "worker",
                new InstanceId("test-instance"),
                UUID.randomUUID(),
                Duration.ofSeconds(5),
                runtime.definitions);
        assertThat(firstClaim).isNotNull();
        assertThat(firstClaim.runId()).isEqualTo(first.value());
        assertThat(
                runtime.runs.claim(
                        "worker",
                        new InstanceId("test-instance"),
                        UUID.randomUUID(),
                        Duration.ofSeconds(5),
                        runtime.definitions))
                .isNull();

        runtime.runs.succeed(firstClaim, "worker", null);
        var secondClaim = runtime.runs.claim(
                "worker",
                new InstanceId("test-instance"),
                UUID.randomUUID(),
                Duration.ofSeconds(5),
                runtime.definitions);
        assertThat(secondClaim).isNotNull();
        assertThat(secondClaim.runId()).isEqualTo(second.value());
    }

    /** 同一原定 fire 的广播保留首次目标快照，份额只由目标进程代次领取。 */
    @Test
    void broadcastsOnlyToInstancesPresentAtTheFirstFire() {
        DirectJob<Long, Void> job = workJob(
                JobTarget.all(Duration.ofSeconds(30)),
                OverlapPolicy.SKIP_IF_RUNNING,
                JobRunRecording.PERSISTED);
        Runtime runtime = runtime(List.of(job));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        runtime.runs.registerInstance(new InstanceId("a"), a, "[\"JOB:WORKER\"]", Duration.ofMinutes(1));
        runtime.runs.registerInstance(new InstanceId("b"), b, "[\"JOB:WORKER\"]", Duration.ofMinutes(1));

        ScheduleKey schedule = new ScheduleKey("TEST/BROADCAST");
        Instant fire = Instant.parse("2026-10-09T00:00:00Z");
        var first = runtime.executor.broadcastScheduled(job, 42L, schedule, fire, OverlapPolicy.SKIP_IF_RUNNING);
        assertThat(first.targets()).containsExactlyInAnyOrder(new InstanceId("a"), new InstanceId("b"));
        assertThat(first.runs()).hasSize(2);

        runtime.runs.registerInstance(
                new InstanceId("c"),
                UUID.randomUUID(),
                "[\"JOB:WORKER\"]",
                Duration.ofMinutes(1));
        var duplicate = runtime.executor.broadcastScheduled(job, 42L, schedule, fire, OverlapPolicy.SKIP_IF_RUNNING);
        assertThat(duplicate.id()).isEqualTo(first.id());
        assertThat(duplicate.targets()).containsExactlyInAnyOrder(new InstanceId("a"), new InstanceId("b"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM job.job_run", Integer.class)).isEqualTo(2);

        var claim = runtime.runs.claim(
                "other-worker",
                new InstanceId("a"),
                UUID.randomUUID(),
                Duration.ofSeconds(5),
                runtime.definitions);
        assertThat(claim).isNull();
        var ownClaim = runtime.runs.claim(
                "a-worker",
                new InstanceId("a"),
                a,
                Duration.ofSeconds(5),
                runtime.definitions);
        assertThat(ownClaim).isNotNull();
        assertThat(ownClaim.runId()).isEqualTo(first.runs().get(new InstanceId("a")).value());
    }

    /** 无记录广播只保存短期投递，不创建 Job Run。 */
    @Test
    void broadcastsUnrecordedWorkWithoutCreatingRuns() {
        DirectJob<Long, Void> job = workJob(
                JobTarget.all(Duration.ofSeconds(30)),
                OverlapPolicy.SKIP_IF_RUNNING,
                JobRunRecording.NONE);
        Runtime runtime = runtime(List.of(job));
        UUID a = UUID.randomUUID();
        runtime.runs.registerInstance(new InstanceId("a"), a, "[\"JOB:WORKER\"]", Duration.ofMinutes(1));

        var dispatch = runtime.executor.broadcast(job, 42L);
        assertThat(dispatch.targets()).containsExactly(new InstanceId("a"));
        assertThat(dispatch.runs()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM job.job_run", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM job.job_delivery", Integer.class)).isOne();

        var delivery = runtime.runs.claimDelivery(
                new InstanceId("a"),
                a,
                Duration.ofSeconds(5),
                runtime.definitions);
        assertThat(delivery).isNotNull();
        assertThat(runtime.runs.completeDelivery(delivery, a, true, null)).isTrue();
    }

    /** 同一 Job key 的两个定义版本各用自己的代码领取已有 Run。 */
    @Test
    void restoresRunsUsingTheirSubmittedDefinitionVersion() {
        DirectJob<Long, Void> previous = workJob(
                1,
                JobTarget.any(),
                OverlapPolicy.ALLOW,
                JobRunRecording.PERSISTED);
        DirectJob<Long, Void> current = workJob(
                2,
                JobTarget.any(),
                OverlapPolicy.ALLOW,
                JobRunRecording.PERSISTED);
        Runtime runtime = runtime(List.of(previous, current));
        Id oldRun = runtime.executor.submit(previous, 42L).runId().orElseThrow();
        Id newRun = runtime.executor.submit(current, 42L).runId().orElseThrow();
        runtime.worker.start();
        try {
            assertThat(runtime.executor.await(oldRun, previous, Duration.ofSeconds(10))).isNull();
            assertThat(runtime.executor.await(newRun, current, Duration.ofSeconds(10))).isNull();
            assertThat(
                    jdbc.queryForObject(
                            "SELECT count(*) FROM job.job_run WHERE status = 'SUCCEEDED'",
                            Integer.class))
                    .isEqualTo(2);
        } finally {
            runtime.worker.stop();
        }
    }

    /** 同键相同输入复用运行，不同输入明确跳过而不返回错误的旧结果。 */
    @Test
    void distinguishesDuplicateSubmissionFromBusyWork() {
        DirectJob<Long, Void> job = workJob(
                1,
                JobTarget.any(),
                OverlapPolicy.SKIP_IF_RUNNING,
                JobRunRecording.PERSISTED,
                true);
        Runtime runtime = runtime(List.of(job));
        var first = runtime.executor.submit(job, 1L);
        var duplicate = runtime.executor.submit(job, 1L);
        var busy = runtime.executor.submit(job, 2L);

        assertThat(first.status()).isEqualTo(JobSubmission.Status.CREATED);
        assertThat(duplicate.status()).isEqualTo(JobSubmission.Status.EXISTING);
        assertThat(duplicate.runId()).contains(first.runId().orElseThrow());
        assertThat(busy.status()).isEqualTo(JobSubmission.Status.SKIPPED);
        assertThat(busy.runId()).isEmpty();
    }

    /** 无记录工作以系统身份运行，业务自身的冲突异常不能伪装成调度跳过。 */
    @Test
    void unrecordedJobUsesSystemCallAndPreservesBusinessConflict() {
        AtomicReference<Long> principalId = new AtomicReference<>();
        DirectJob<Void, Void> job = DirectJob.unrecorded(
                new JobKey("TEST/UNRECORDED"),
                1,
                TreeCode.ofColon("JOB:WORKER"),
                () -> {
                    principalId.set(CallHolder.current().principal().orElseThrow().principalId());
                    throw new BusinessException(CoreErrorCode.CONFLICT);
                });
        Runtime runtime = runtime(List.of(job));
        var capabilities = new InstanceCapabilities(
                List.of(
                        new InstanceCapability(
                                TreeCode.ofColon("JOB:WORKER"),
                                SystemPrincipals.MAIN,
                                List.of())),
                null);
        var executor = new DefaultJobExecutor(
                new JobCatalog(List.of(job)),
                runtime.runs,
                new Sca4djMapper(),
                capabilities,
                new InstanceProperties(),
                immediateLock(),
                Clock.systemUTC(),
                new JobProperties());

        assertThatThrownBy(() -> executor.trigger(job, null, OverlapPolicy.SKIP_IF_RUNNING))
                .isInstanceOf(BusinessException.class);
        assertThat(principalId.get()).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM job.job_run", Integer.class)).isZero();
    }

    /** 仅运行调度的实例把无记录工作交给在线的 Job Worker。 */
    @Test
    void schedulerWithoutJobCapabilityRoutesUnrecordedWorkToAnEligibleInstance() {
        DirectJob<Void, Void> job = DirectJob.unrecorded(
                new JobKey("TEST/REMOTE_SCAN"),
                1,
                TreeCode.ofColon("JOB:WORKER"),
                () -> {
                });
        Runtime runtime = runtime(List.of(job));
        UUID generation = UUID.randomUUID();
        runtime.runs.registerInstance(
                new InstanceId("worker-a"),
                generation,
                "[\"JOB:WORKER\"]",
                Duration.ofMinutes(1));
        var capability = new InstanceCapability(
                TreeCode.ofColon("JOB:WORKER"),
                SystemPrincipals.MAIN,
                List.of());
        var schedulerCapabilities = new InstanceCapabilities(List.of(capability), List.of());
        var scheduler = new DefaultJobExecutor(
                new JobCatalog(List.of(job)),
                runtime.runs,
                new Sca4djMapper(),
                schedulerCapabilities,
                new InstanceProperties(),
                immediateLock(),
                Clock.systemUTC(),
                new JobProperties());

        assertThat(
                scheduler.triggerScheduled(
                        job,
                        null,
                        new ScheduleKey("TEST/REMOTE_SCAN"),
                        Instant.parse("2026-10-09T00:00:00Z"),
                        OverlapPolicy.SKIP_IF_RUNNING))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM job.job_run", Integer.class)).isZero();
        assertThat(
                jdbc.queryForObject(
                        "SELECT count(*) FROM job.job_delivery WHERE target_instance_id = 'worker-a'",
                        Integer.class))
                .isOne();
    }

    private static DirectJob<Long, Void> workJob(
            JobTarget target,
            OverlapPolicy overlap,
            JobRunRecording recording) {
        return workJob(1, target, overlap, recording);
    }

    private static DirectJob<Long, Void> workJob(
            int version,
            JobTarget target,
            OverlapPolicy overlap,
            JobRunRecording recording) {
        return workJob(version, target, overlap, recording, false);
    }

    private static DirectJob<Long, Void> workJob(
            int version,
            JobTarget target,
            OverlapPolicy overlap,
            JobRunRecording recording,
            boolean fixedKey) {
        return new DirectJob<>() {
            @Override
            public @NotNull JobKey key() {
                return new JobKey("TEST/WORK");
            }

            @Override
            public int definitionVersion() {
                return version;
            }

            @Override
            public @NotNull Class<Long> inputType() {
                return Long.class;
            }

            @Override
            public @NotNull Class<Void> outputType() {
                return Void.class;
            }

            @Override
            public @NotNull RetryRule retryRule() {
                return RetryRule.once();
            }

            @Override
            public @NotNull JobRunRecording runRecording() {
                return recording;
            }

            @Override
            public @NotNull JobTarget target(Long input) {
                return target;
            }

            @Override
            public @NotNull OverlapPolicy overlapPolicy() {
                return overlap;
            }

            @Override
            public @NotNull JobWorkKey workKey(Long input) {
                return fixedKey
                        ? JobWorkKey.fixed(key())
                        : JobWorkKey.of(TreeCode.ofSegment("TEST"), input);
            }

            @Override
            protected @Nullable Void execute(@NotNull JobExecution execution, Long input) {
                return null;
            }
        };
    }

    private static void awaitBothBranches(CountDownLatch bothBranches) {
        bothBranches.countDown();
        try {
            if (!bothBranches.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("并发分支未能同时开始");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待并发分支被中断", exception);
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
            public @NotNull RetryRule retryRule() {
                return RetryRule.once();
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
                        "SELECT app_name FROM job.job_run WHERE id = ?",
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
                                + "retry_rule, attempt_count, available_at, completed_at) "
                                + "VALUES (?, 'direct', 'DIRECT', 'SUCCEEDED', 0, CAST(? AS JSONB), 1, ?, ?) RETURNING id",
                        Long.class,
                        firstExpired,
                        retryRules.encode(RetryRule.once()),
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
                            RetryRule.once());

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
                new Sca4djMapper());
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
                                + "(app_name, job_key, job_definition_version, job_kind, status, "
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
                        + "(run_id, node_key, node_kind, output_json, status, parallel_merge, retry_rule, "
                        + "attempt_count, available_at, completed_at) "
                        + "VALUES (?, ?, 'PIPELINE', CAST(? AS JSONB), 'SUCCEEDED', 0, CAST(? AS JSONB), 1, ?, ?)",
                runId,
                "outcome",
                outputJson,
                retryRules.encode(RetryRule.once()),
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
                        "INSERT INTO job.job_run_node (run_id, node_key, node_kind, status, retry_rule, attempt_count, available_at, lease_owner, lease_until, output_json) VALUES (?, 'direct', 'DIRECT', 'RUNNING', CAST(? AS JSONB), 2, ?, 'new', ?, '\"old\"'::jsonb) RETURNING id",
                        Long.class,
                        runId,
                        retryRules.encode(RetryRule.atMost(3, Backoff.fixed(Duration.ZERO))),
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
                JobKind.DIRECT,
                "direct",
                null,
                1,
                RetryRule.atMost(3, Backoff.fixed(Duration.ZERO)));
        var current = new NodeClaim(
                nodeId,
                runId,
                new JobKey("TEST"),
                1,
                JobKind.DIRECT,
                "direct",
                null,
                2,
                RetryRule.atMost(3, Backoff.fixed(Duration.ZERO)));
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

    /** 固定、指数、序列和无限规则在失败写回后保留相同的持久化等待。 */
    @Test
    void persistsAllRetryRulesAndRecoversExpiredLeaseWithoutResettingDelay() {
        Instant now = Instant.parse("2026-10-12T00:00:00Z");
        List<RetryRule> rules = List.of(
                RetryRule.atMost(4, Backoff.fixed(Duration.ofSeconds(5))),
                RetryRule.atMost(4, Backoff.exponential(Duration.ofSeconds(1), 2, Duration.ofSeconds(10))),
                RetryRule.atMost(4, Backoff.sequence(Duration.ofSeconds(1), Duration.ofSeconds(7))),
                RetryRule.unlimited(Backoff.fixed(Duration.ofSeconds(9))));
        JobRepository repository = new JobRepository(mapper(), sqlBatches());
        JobRunService runs = new JobRunService(
                repository,
                new DataSourceTransactionManager(dataSource),
                Clock.fixed(now, ZoneOffset.UTC),
                immediateLock(),
                new Sca4djMapper());
        for (RetryRule rule : rules) {
            String json = retryRules.encode(rule);
            long runId = insertRun(APPLICATION_NAME, "RUNNING", null);
            long nodeId = Objects.requireNonNull(
                    jdbc.queryForObject(
                            "INSERT INTO job.job_run_node (run_id, node_key, node_kind, status, retry_rule, attempt_count, available_at, lease_owner, lease_until) "
                                    + "VALUES (?, 'direct', 'DIRECT', 'RUNNING', CAST(? AS JSONB), 2, ?, 'worker', ?) RETURNING id",
                            Long.class,
                            runId,
                            json,
                            Timestamp.from(now),
                            Timestamp.from(now.plusSeconds(60))));
            jdbc.update(
                    "INSERT INTO job.job_run_attempt (node_id, attempt_number, worker_id, status, started_at) VALUES (?, 2, 'worker', 'RUNNING', ?)",
                    nodeId,
                    Timestamp.from(now));
            NodeClaim claim = new NodeClaim(
                    nodeId,
                    runId,
                    new JobKey("TEST/HISTORY"),
                    1,
                    JobKind.DIRECT,
                    "direct",
                    null,
                    2,
                    rule);
            assertThat(runs.fail(claim, "worker", "temporary").retrying()).isTrue();
            assertThat(
                    Objects.requireNonNull(
                            jdbc.queryForObject(
                                    "SELECT available_at FROM job.job_run_node WHERE id = ?",
                                    Timestamp.class,
                                    nodeId))
                            .toInstant())
                    .isEqualTo(now.plus(rule.backoff().delayAfter(2)));
            // 模拟下一次进程失联；恢复从原租约截止时间计算，不从每次扫描时间重新计时。
            Instant expiredAt = now.minusSeconds(1);
            jdbc.update(
                    "UPDATE job.job_run_node SET status = 'RUNNING', lease_owner = 'worker', lease_until = ? WHERE id = ?",
                    Timestamp.from(expiredAt),
                    nodeId);
            jdbc.update(
                    "UPDATE job.job_run_attempt SET status = 'RUNNING', completed_at = NULL WHERE node_id = ?",
                    nodeId);
            runs.recoverExpiredAttempts();
            Instant next = Objects.requireNonNull(
                    jdbc
                            .queryForObject(
                                    "SELECT available_at FROM job.job_run_node WHERE id = ?",
                                    Timestamp.class,
                                    nodeId))
                    .toInstant();
            assertThat(next).isEqualTo(expiredAt.plus(rule.backoff().delayAfter(2)));
            runs.recoverExpiredAttempts();
            assertThat(
                    Objects.requireNonNull(
                            jdbc.queryForObject(
                                    "SELECT available_at FROM job.job_run_node WHERE id = ?",
                                    Timestamp.class,
                                    nodeId))
                            .toInstant())
                    .isEqualTo(next);
            assertThat(
                    jdbc.queryForObject(
                            "SELECT status FROM job.job_run_attempt WHERE node_id = ?",
                            String.class,
                            nodeId))
                    .isEqualTo("ABANDONED");
            assertThatThrownBy(() -> runs.succeed(claim, "worker", null)).isInstanceOf(OptimisticLockException.class);
        }
    }

    /** 插入一个用于历史保留测试的根运行。 */
    private long insertRun(@NotNull String namespace, @NotNull String status, @Nullable Instant completedAt) {
        Instant createdAt = completedAt == null
                ? Instant.parse("2026-08-09T00:00:00Z")
                : completedAt.minusSeconds(1);
        return Objects.requireNonNull(
                jdbc.queryForObject(
                        "INSERT INTO job.job_run "
                                + "(app_name, job_key, job_definition_version, job_kind, status, created_at, completed_at) "
                                + "VALUES (?, 'TEST/HISTORY', 1, 'DIRECT', ?, ?, ?) RETURNING id",
                        Long.class,
                        namespace,
                        status,
                        Timestamp.from(createdAt),
                        completedAt == null ? null : Timestamp.from(completedAt)));
    }

    /** 创建使用同一真实 DataSource 的 Repository、Executor 和 worker。 */
    private Runtime runtime(List<? extends Job<?, ?>> jobs) {
        Sca4djMapper objectMapper = new Sca4djMapper();
        JobCatalog catalog = new JobCatalog(List.copyOf(jobs));
        JobMapper mapper = mapper();
        JobRepository repository = new JobRepository(mapper, sqlBatches());
        JobRunService runs = new JobRunService(
                repository,
                new DataSourceTransactionManager(dataSource),
                Clock.systemUTC(),
                immediateLock(),
                objectMapper);
        JobProperties properties = new JobProperties();
        properties.setWorkerCount(4);
        properties.setClaimBatchSize(4);
        properties.setPollInterval(Duration.ofMillis(10));
        properties.setLeaseDuration(Duration.ofSeconds(5));
        properties.setLeaseRenewInterval(Duration.ofSeconds(1));
        properties.validate();
        InstanceCapabilities capabilities = new InstanceCapabilities(
                List.of(
                        new InstanceCapability(
                                TreeCode.ofColon("JOB:WORKER"),
                                SystemPrincipals.MAIN,
                                List.of())),
                null);
        InstanceProperties instance = new InstanceProperties();
        DefaultJobExecutor executor = new DefaultJobExecutor(
                catalog,
                runs,
                objectMapper,
                capabilities,
                instance,
                immediateLock(),
                Clock.systemUTC(),
                properties);
        JobWorker worker = new JobWorker(
                catalog,
                runs,
                objectMapper,
                properties,
                Clock.systemUTC(),
                instance,
                capabilities);
        return new Runtime(executor, worker, runs, catalog.availableVersions(capabilities));
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
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setArgNameBasedConstructorAutoMapping(true);
            configuration.setDefaultEnumTypeHandler(CoreEnumTypeHandler.class);
            configuration.getTypeHandlerRegistry()
                    .register(RetryRule.class, new StringValueTypeHandler<>(retryRules::decode, retryRules::encode));
            configuration.getTypeHandlerRegistry()
                    .register(
                            Name.class,
                            new StringValueTypeHandler<>(Name::new, name -> name.format(NameFormat.COLON)));
            configuration.getTypeHandlerRegistry()
                    .register(
                            TreeCode.class,
                            new TreeCodeTypeHandler.Colon());
            configuration.getTypeHandlerRegistry()
                    .register(
                            JobKey.class,
                            new StringValueTypeHandler<>(JobKey::new, key -> key.format(NameFormat.SLASH)));
            configuration.getTypeHandlerRegistry()
                    .register(
                            ScheduleKey.class,
                            new StringValueTypeHandler<>(ScheduleKey::new, key -> key.format(NameFormat.SLASH)));
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
        private final JobRunService runs;

        private final DefaultJobExecutor executor;

        private final JobWorker worker;

        private final Map<JobKey, Map<Integer, InstanceCapability>> definitions;

        private Runtime(
                DefaultJobExecutor executor,
                JobWorker worker,
                JobRunService runs,
                Map<JobKey, Map<Integer, InstanceCapability>> definitions) {
            this.executor = executor;
            this.worker = worker;
            this.runs = runs;
            this.definitions = definitions;
        }
    }
}
