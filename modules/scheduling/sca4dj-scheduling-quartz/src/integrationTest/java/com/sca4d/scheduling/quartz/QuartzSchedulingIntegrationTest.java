package com.sca4d.scheduling.quartz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.job.DirectJob;
import com.sca4d.core.api.job.Job;
import com.sca4d.core.api.job.JobExecution;
import com.sca4d.core.api.job.JobExecutor;
import com.sca4d.core.api.job.JobKey;
import com.sca4d.core.api.job.JobRetryPolicy;
import com.sca4d.core.api.job.JobRunStatus;
import com.sca4d.core.api.scheduling.ClusterTask;
import com.sca4d.core.api.scheduling.ClusterTaskSchedule;
import com.sca4d.core.api.scheduling.ClusterTaskScheduler;
import com.sca4d.core.api.scheduling.IntervalSchedule;
import com.sca4d.core.api.scheduling.JobSchedule;
import com.sca4d.core.api.scheduling.JobScheduler;
import com.sca4d.core.api.scheduling.MisfirePolicy;
import com.sca4d.core.api.scheduling.OverlapPolicy;
import com.sca4d.core.api.scheduling.ScheduleKey;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.persistence.mybatis.CoreMyBatisAutoConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.sql.DataSource;

/** 使用真实 PostgreSQL 验证 Quartz 集群表、代码计划对账和 fire 提交。 */
@Testcontainers(disabledWithoutDocker = true)
class QuartzSchedulingIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static DataSource dataSource;

    /** 在 Spring 创建 Scheduler 前准备官方 Quartz PostgreSQL 表。 */
    @BeforeAll
    static void createSchema() {
        dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        new ResourceDatabasePopulator(new ClassPathResource("META-INF/sca4dj/schema/quartz.sql"))
                .execute(dataSource);
    }

    /** 对账完成后 Scheduler 才启动，并把实际 fire 提交给 JobExecutor。 */
    @Test
    void reconcilesStartsAndDispatchesScheduledFire() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                QuartzSchedulingAutoConfiguration.class,
                                CoreMyBatisAutoConfiguration.class,
                                MybatisAutoConfiguration.class,
                                QuartzAutoConfiguration.class))
                .withUserConfiguration(TestConfiguration.class)
                .withPropertyValues(
                        "spring.application.name=quartz-test",
                        "sca4dj.scheduling.quartz.thread-count=2",
                        "sca4dj.scheduling.quartz.cluster-checkin-interval=PT1S");

        runner.run(context -> {
            assertThat(context).hasNotFailed();
            CountingJobExecutor executor = context.getBean(CountingJobExecutor.class);
            assertThat(executor.fire.await(10, TimeUnit.SECONDS)).isTrue();
            CountingClusterTask clusterTask = context.getBean(CountingClusterTask.class);
            assertThat(clusterTask.execution.await(10, TimeUnit.SECONDS)).isTrue();

            Scheduler scheduler = context.getBean(Scheduler.class);
            assertThat(scheduler.isStarted()).isTrue();
            assertThat(scheduler.getMetaData().isJobStoreClustered()).isTrue();
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(
                    jdbc.queryForObject(
                            "SELECT count(*) FROM quartz.qrtz_job_details "
                                    + "WHERE sched_name = 'QUARTZ_TEST:SCHEDULING:QUARTZ'",
                            Integer.class))
                    .isEqualTo(2);
            assertThat(
                    jdbc.queryForObject(
                            "SELECT count(*) FROM quartz.qrtz_triggers "
                                    + "WHERE sched_name = 'QUARTZ_TEST:SCHEDULING:QUARTZ'",
                            Integer.class))
                    .isEqualTo(2);
            assertThat(
                    jdbc.queryForObject(
                            "SELECT is_nonconcurrent FROM quartz.qrtz_job_details "
                                    + "WHERE sched_name = 'QUARTZ_TEST:SCHEDULING:QUARTZ' "
                                    + "AND job_name = 'TEST/QUARTZ/CLUSTER_TASK' "
                                    + "AND job_group = 'sca4dj-cluster-task'",
                            Boolean.class))
                    .isTrue();

            // 持久化定义缺少当前版本键时，以当前代码定义重建 JobDetail。
            scheduler.standby();
            JobDetail incompleteSchedule = scheduler.getJobDetail(
                    new org.quartz.JobKey("TEST/QUARTZ/DISPATCH", "sca4dj-code"));
            incompleteSchedule.getJobDataMap().remove(QuartzDispatchJob.SCHEDULE_DEFINITION_VERSION);
            scheduler.addJob(incompleteSchedule, true);
            JobDetail incompleteClusterTask = scheduler.getJobDetail(
                    new org.quartz.JobKey("TEST/QUARTZ/CLUSTER_TASK", "sca4dj-cluster-task"));
            incompleteClusterTask.getJobDataMap().remove(QuartzClusterTaskJob.TASK_DEFINITION_VERSION);
            scheduler.addJob(incompleteClusterTask, true);

            JobScheduler jobScheduler = context.getBean(JobScheduler.class);
            JobSchedule<Void, Void> schedule = context.getBean(JobSchedule.class);
            scheduler.addJob(
                    JobBuilder.newJob(QuartzDispatchJob.class)
                            .withIdentity("storage.file.cleanup", "sca4dj-code")
                            .storeDurably()
                            .build(),
                    true);
            scheduler.addJob(
                    JobBuilder.newJob(QuartzClusterTaskJob.class)
                            .withIdentity("iot.downlink.retention", "sca4dj-cluster-task")
                            .storeDurably()
                            .build(),
                    true);
            jobScheduler.reconcile(List.of(schedule));
            ClusterTaskScheduler clusterTaskScheduler = context.getBean(ClusterTaskScheduler.class);
            ClusterTaskSchedule clusterTaskSchedule = context.getBean(ClusterTaskSchedule.class);
            clusterTaskScheduler.reconcileClusterTasks(List.of(clusterTaskSchedule));
            assertThat(scheduler.checkExists(new org.quartz.JobKey("storage.file.cleanup", "sca4dj-code")))
                    .isFalse();
            assertThat(scheduler.checkExists(new org.quartz.JobKey("iot.downlink.retention", "sca4dj-cluster-task")))
                    .isFalse();
            assertThat(
                    scheduler.getJobDetail(new org.quartz.JobKey("TEST/QUARTZ/DISPATCH", "sca4dj-code"))
                            .getJobDataMap())
                    .containsEntry(QuartzDispatchJob.SCHEDULE_DEFINITION_VERSION, "1");
            assertThat(
                    scheduler.getJobDetail(
                            new org.quartz.JobKey(
                                    "TEST/QUARTZ/CLUSTER_TASK",
                                    "sca4dj-cluster-task"))
                            .getJobDataMap())
                    .containsEntry(QuartzClusterTaskJob.TASK_DEFINITION_VERSION, "1");
            scheduler.start();

            // 历史记录缺少当前调度契约时，运行时严格拒绝，不在公共机制中保留历史兼容分支。
            scheduler.standby();
            JobDetail legacyClusterTask = scheduler.getJobDetail(
                    new org.quartz.JobKey("TEST/QUARTZ/CLUSTER_TASK", "sca4dj-cluster-task"));
            String currentClusterContract = legacyClusterTask.getJobDataMap().getString("scheduleContract");
            String currentClusterFingerprint = legacyClusterTask.getJobDataMap().getString("scheduleFingerprint");
            legacyClusterTask.getJobDataMap().remove("scheduleContract");
            legacyClusterTask.getJobDataMap().put("scheduleFingerprint", "legacy-implementation-fingerprint");
            scheduler.addJob(legacyClusterTask, true);
            assertThatThrownBy(() -> clusterTaskScheduler.reconcileClusterTasks(List.of(clusterTaskSchedule)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("同 definitionVersion 定义发生变化");
            legacyClusterTask.getJobDataMap().put("scheduleContract", currentClusterContract);
            legacyClusterTask.getJobDataMap().put("scheduleFingerprint", currentClusterFingerprint);
            scheduler.addJob(legacyClusterTask, true);

            // 任务实现类更换但调度契约不变时不需要递增 definitionVersion。
            ClusterTaskSchedule movedTask = new ClusterTaskSchedule(
                    clusterTaskSchedule.key(),
                    clusterTaskSchedule.definitionVersion(),
                    new RenamedCountingClusterTask(),
                    clusterTaskSchedule.timing(),
                    clusterTaskSchedule.misfirePolicy());
            clusterTaskScheduler.reconcileClusterTasks(List.of(movedTask));
            JobSchedule<Void, Void> movedSchedule = new JobSchedule<>(
                    schedule.key(),
                    schedule.definitionVersion(),
                    new RenamedDirectJob(),
                    schedule.input(),
                    schedule.timing(),
                    schedule.misfirePolicy(),
                    schedule.overlapPolicy());
            jobScheduler.reconcile(List.of(movedSchedule));
            scheduler.start();

            JobSchedule<?, ?> changedWithoutVersion = withInterval(
                    schedule,
                    new IntervalSchedule(Duration.ofSeconds(2), Duration.ofMillis(100)));
            assertThatThrownBy(() -> jobScheduler.reconcile(List.of(changedWithoutVersion)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("同 definitionVersion 定义发生变化");

            scheduler.standby();
            jdbc.update(
                    "UPDATE quartz.qrtz_triggers SET trigger_state = 'ERROR' "
                            + "WHERE sched_name = 'QUARTZ_TEST:SCHEDULING:QUARTZ' "
                            + "AND trigger_name = 'TEST/QUARTZ/DISPATCH'");
            jobScheduler.reconcile(List.of(schedule));
            assertThat(
                    jdbc.queryForObject(
                            "SELECT trigger_state FROM quartz.qrtz_triggers "
                                    + "WHERE sched_name = 'QUARTZ_TEST:SCHEDULING:QUARTZ' "
                                    + "AND trigger_name = 'TEST/QUARTZ/DISPATCH'",
                            String.class))
                    .isEqualTo("WAITING");
            scheduler.start();
        });
    }

    /** 没有 JobExecutor 时仍可只安装并执行基础设施集群任务。 */
    @Test
    void runsClusterTaskWithoutJobExecutor() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                QuartzSchedulingAutoConfiguration.class,
                                CoreMyBatisAutoConfiguration.class,
                                MybatisAutoConfiguration.class,
                                QuartzAutoConfiguration.class))
                .withUserConfiguration(TestConfiguration.class)
                .withPropertyValues(
                        "spring.application.name=quartz-test",
                        "test.job-schedule.enabled=false",
                        "sca4dj.scheduling.quartz.thread-count=2",
                        "sca4dj.scheduling.quartz.cluster-checkin-interval=PT1S");

        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(JobExecutor.class);
            CountingClusterTask clusterTask = context.getBean(CountingClusterTask.class);
            assertThat(clusterTask.execution.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(context.getBean(Scheduler.class).isStarted()).isTrue();
        });
    }

    /** 保留 JobSchedule 输入输出泛型关系，只替换测试需要的固定间隔。 */
    private static <I, O> @NotNull JobSchedule<I, O> withInterval(
            @NotNull JobSchedule<I, O> schedule,
            @NotNull IntervalSchedule interval) {
        return new JobSchedule<>(
                schedule.key(),
                schedule.definitionVersion(),
                schedule.job(),
                schedule.input(),
                interval,
                schedule.misfirePolicy(),
                schedule.overlapPolicy());
    }

    /** 提供真实 DataSource、统一 JSON、代码计划和可观测 JobExecutor。 */
    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {

        @Bean
        DataSource dataSource() {
            return dataSource;
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource source) {
            return new JdbcTemplate(source);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        /** @return 立即执行任务的单进程测试锁 */
        @Bean
        DistributedLock distributedLock() {
            return new DistributedLock() {
                @Override
                public <T> T execute(LockKey key, Duration waitTime, Supplier<T> task) {
                    return task.get();
                }
            };
        }

        @Bean
        @Sca4djJson
        ObjectMapper sca4djJson() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        @ConditionalOnProperty(name = "test.job-schedule.enabled", havingValue = "true", matchIfMissing = true)
        CountingJobExecutor jobExecutor() {
            return new CountingJobExecutor();
        }

        @Bean
        @ConditionalOnProperty(name = "test.job-schedule.enabled", havingValue = "true", matchIfMissing = true)
        DirectJob<Void, Void> scheduledJob() {
            return new DirectJob<>() {
                private final JobKey key = new JobKey("TEST/QUARTZ/DISPATCH");

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
                protected @Nullable Void execute(
                        @NotNull JobExecution execution,
                        @Nullable Void input) {
                    return null;
                }
            };
        }

        @Bean
        @ConditionalOnProperty(name = "test.job-schedule.enabled", havingValue = "true", matchIfMissing = true)
        JobSchedule<Void, Void> schedule(DirectJob<Void, Void> scheduledJob) {
            return new JobSchedule<>(
                    new ScheduleKey("TEST/QUARTZ/DISPATCH"),
                    1,
                    scheduledJob,
                    null,
                    new IntervalSchedule(Duration.ofSeconds(1), Duration.ofMillis(100)),
                    MisfirePolicy.FIRE_ONCE_NOW,
                    OverlapPolicy.SKIP_IF_RUNNING);
        }

        @Bean
        CountingClusterTask countingClusterTask() {
            return new CountingClusterTask();
        }

        @Bean
        ClusterTaskSchedule clusterTaskSchedule(CountingClusterTask task) {
            return new ClusterTaskSchedule(
                    new ScheduleKey("TEST/QUARTZ/CLUSTER_TASK"),
                    1,
                    task,
                    new IntervalSchedule(Duration.ofMillis(100), Duration.ofMillis(50)),
                    MisfirePolicy.FIRE_ONCE_NOW);
        }
    }

    /** 记录 Quartz 是否直接执行了集群任务。 */
    static final class CountingClusterTask implements ClusterTask {
        private final CountDownLatch execution = new CountDownLatch(1);

        @Override
        public void execute() {
            execution.countDown();
        }
    }

    /** 用于验证实现类更换不改变稳定调度契约。 */
    static final class RenamedCountingClusterTask implements ClusterTask {
        @Override
        public void execute() {
        }
    }

    /** 用于验证 Job 实现类更换不改变稳定调度契约。 */
    static final class RenamedDirectJob extends DirectJob<Void, Void> {
        private final JobKey key = new JobKey("TEST/QUARTZ/DISPATCH");

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
        protected @Nullable Void execute(
                @NotNull JobExecution execution,
                @Nullable Void input) {
            return null;
        }
    }

    /** 记录 Quartz 是否使用原定 fire 时间提交了计划。 */
    static final class CountingJobExecutor implements JobExecutor {
        private final CountDownLatch fire = new CountDownLatch(1);

        @Override
        public <I, O> @NotNull Id submit(@NotNull Job<I, O> job, @Nullable I input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <I, O> @NotNull Optional<Id> submitScheduled(
                @NotNull Job<I, O> job,
                @Nullable I input,
                @NotNull ScheduleKey scheduleKey,
                @NotNull Instant scheduledAt,
                @NotNull OverlapPolicy overlapPolicy) {
            fire.countDown();
            return Optional.of(Id.of(1));
        }

        @Override
        public <I, O> @Nullable O run(
                @NotNull Job<I, O> job,
                @Nullable I input,
                @NotNull Duration timeout) {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NotNull JobRunStatus status(@NotNull Id runId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <O> @NotNull Optional<O> result(
                @NotNull Id runId,
                @NotNull Job<?, O> job) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <O> @Nullable O await(
                @NotNull Id runId,
                @NotNull Job<?, O> job,
                @NotNull Duration timeout) {
            throw new UnsupportedOperationException();
        }
    }
}
