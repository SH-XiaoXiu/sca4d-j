package com.sca4d.scheduling.quartz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.identity.SystemPrincipal;
import com.sca4d.core.api.identity.SystemPrincipals;
import com.sca4d.core.api.isolation.AppName;
import com.sca4d.core.api.job.DirectJob;
import com.sca4d.core.api.job.Job;
import com.sca4d.core.api.job.JobDispatch;
import com.sca4d.core.api.job.JobExecutor;
import com.sca4d.core.api.job.JobKey;
import com.sca4d.core.api.job.JobRunStatus;
import com.sca4d.core.api.job.JobSubmission;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.retry.RetryRule;
import com.sca4d.core.api.runtime.InstanceCapabilities;
import com.sca4d.core.api.runtime.InstanceCapability;
import com.sca4d.core.api.scheduling.IntervalSchedule;
import com.sca4d.core.api.scheduling.JobSchedule;
import com.sca4d.core.api.scheduling.JobScheduler;
import com.sca4d.core.api.scheduling.MisfirePolicy;
import com.sca4d.core.api.scheduling.OverlapPolicy;
import com.sca4d.core.api.scheduling.ScheduleKey;
import com.sca4d.core.spring.config.CoreJacksonAutoConfiguration;
import com.sca4d.core.spring.config.InstanceProperties;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.json.Sca4djMapper;
import com.sca4d.core.spring.persistence.mybatis.CoreMyBatisAutoConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.sql.DataSource;

/** 使用真实 PostgreSQL 验证统一 JobSchedule 的两种运行记录策略。 */
@Testcontainers(disabledWithoutDocker = true)
class QuartzSchedulingIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static DataSource dataSource;

    @BeforeAll
    static void createSchema() {
        AppName.publish("quartz-test");
        SystemPrincipals.publish(
                Map.of(
                        SystemPrincipals.MAIN,
                        new SystemPrincipal(1, SystemPrincipals.MAIN)));
        dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        new ResourceDatabasePopulator(new ClassPathResource("META-INF/sca4dj/schema/quartz.sql"))
                .execute(dataSource);
    }

    @Test
    @SuppressWarnings("unchecked")
    void schedulesPersistedAndUnrecordedJobsAndRemovesOldTaskGroup() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                QuartzSchedulingAutoConfiguration.class,
                                CoreJacksonAutoConfiguration.class,
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
            assertThat(executor.persisted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(executor.unrecorded.await(10, TimeUnit.SECONDS)).isTrue();

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

            scheduler.standby();
            scheduler.addJob(
                    JobBuilder.newJob(QuartzDispatchJob.class)
                            .withIdentity("old-scan", "sca4dj-cluster-task")
                            .storeDurably()
                            .build(),
                    true);
            JobDetail incomplete = scheduler.getJobDetail(
                    new org.quartz.JobKey("TEST/QUARTZ/DISPATCH", "sca4dj-code"));
            incomplete.getJobDataMap().remove(QuartzDispatchJob.SCHEDULE_DEFINITION_VERSION);
            scheduler.addJob(incomplete, true);

            JobScheduler schedules = context.getBean(JobScheduler.class);
            List<JobSchedule<?, ?>> declared = List.of(
                    (JobSchedule<Void, Void>) context.getBean("persistedSchedule", JobSchedule.class),
                    (JobSchedule<Void, Void>) context.getBean("unrecordedSchedule", JobSchedule.class));
            schedules.reconcile(declared);
            assertThat(scheduler.checkExists(new org.quartz.JobKey("old-scan", "sca4dj-cluster-task")))
                    .isFalse();
            assertThat(
                    scheduler.getJobDetail(
                            new org.quartz.JobKey("TEST/QUARTZ/DISPATCH", "sca4dj-code"))
                            .getJobDataMap())
                    .containsEntry(QuartzDispatchJob.SCHEDULE_DEFINITION_VERSION, "1");

            JobSchedule<?, ?> changed = new JobSchedule<>(
                    new ScheduleKey("TEST/QUARTZ/DISPATCH"),
                    1,
                    context.getBean("persistedJob", DirectJob.class),
                    null,
                    new IntervalSchedule(Duration.ofSeconds(2), Duration.ofMillis(100)),
                    MisfirePolicy.FIRE_ONCE_NOW,
                    OverlapPolicy.SKIP_IF_RUNNING);
            assertThatThrownBy(
                    () -> schedules.reconcile(
                            List.of(
                                    changed,
                                    context.getBean(
                                            "unrecordedSchedule",
                                            JobSchedule.class))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("同 definitionVersion 定义发生变化");
        });
    }

    /** 单独安装调度模块时也能执行无记录 Job，不要求持久化 JobExecutor。 */
    @Test
    void runsUnrecordedScheduleWithoutJobModule() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                QuartzSchedulingAutoConfiguration.class,
                                CoreJacksonAutoConfiguration.class,
                                CoreMyBatisAutoConfiguration.class,
                                MybatisAutoConfiguration.class,
                                QuartzAutoConfiguration.class))
                .withUserConfiguration(TestConfiguration.class)
                .withPropertyValues(
                        "spring.application.name=quartz-test",
                        "test.executor.enabled=false",
                        "sca4dj.scheduling.quartz.thread-count=2",
                        "sca4dj.scheduling.quartz.cluster-checkin-interval=PT1S");

        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(JobExecutor.class);
            assertThat(context.getBean(CountingScan.class).execution.await(10, TimeUnit.SECONDS)).isTrue();
        });
    }

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

        @Bean
        InstanceProperties instanceProperties() {
            return new InstanceProperties();
        }

        @Bean
        InstanceCapabilities instanceCapabilities(List<InstanceCapability> definitions) {
            return new InstanceCapabilities(definitions, null);
        }

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
        Sca4djMapper sca4djJson() {
            return new Sca4djMapper();
        }

        @Bean
        @ConditionalOnProperty(name = "test.executor.enabled", havingValue = "true", matchIfMissing = true)
        CountingJobExecutor jobExecutor() {
            return new CountingJobExecutor();
        }

        @Bean
        @ConditionalOnProperty(name = "test.executor.enabled", havingValue = "true", matchIfMissing = true)
        DirectJob<Void, Void> persistedJob() {
            return new DirectJob<>() {
                @Override
                public @NotNull JobKey key() {
                    return new JobKey("TEST/QUARTZ/DISPATCH");
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
                protected @Nullable Void execute(
                        @NotNull com.sca4d.core.api.job.JobExecution execution,
                        @Nullable Void input) {
                    return null;
                }
            };
        }

        @Bean
        CountingScan countingScan() {
            return new CountingScan();
        }

        @Bean
        DirectJob<Void, Void> unrecordedJob(CountingScan scan) {
            return DirectJob.unrecorded(
                    new JobKey("TEST/QUARTZ/SCAN"),
                    1,
                    TreeCode.ofColon("SCHEDULING:WORKER"),
                    scan::execute);
        }

        @Bean
        @ConditionalOnProperty(name = "test.executor.enabled", havingValue = "true", matchIfMissing = true)
        JobSchedule<Void, Void> persistedSchedule(
                @Qualifier("persistedJob") DirectJob<Void, Void> job) {
            return schedule("TEST/QUARTZ/DISPATCH", job);
        }

        @Bean
        JobSchedule<Void, Void> unrecordedSchedule(
                @Qualifier("unrecordedJob") DirectJob<Void, Void> job) {
            return schedule("TEST/QUARTZ/SCAN", job);
        }

        private static JobSchedule<Void, Void> schedule(String key, DirectJob<Void, Void> job) {
            return new JobSchedule<>(
                    new ScheduleKey(key),
                    1,
                    job,
                    null,
                    new IntervalSchedule(Duration.ofSeconds(1), Duration.ofMillis(100)),
                    MisfirePolicy.FIRE_ONCE_NOW,
                    OverlapPolicy.SKIP_IF_RUNNING);
        }
    }

    static class CountingScan {
        private final CountDownLatch execution = new CountDownLatch(1);

        void execute() {
            execution.countDown();
        }
    }

    static class CountingJobExecutor implements JobExecutor {

        private final CountDownLatch persisted = new CountDownLatch(1);

        private final CountDownLatch unrecorded = new CountDownLatch(1);

        @Override
        public <I, O> @NotNull JobSubmission submit(@NotNull Job<I, O> job, @Nullable I input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <I, O> @NotNull Optional<Id> submitScheduled(
                @NotNull Job<I, O> job,
                @Nullable I input,
                @NotNull ScheduleKey scheduleKey,
                @NotNull Instant scheduledAt,
                @NotNull OverlapPolicy overlapPolicy) {
            persisted.countDown();
            return Optional.of(Id.of(1));
        }

        @Override
        public <I, O> @NotNull JobDispatch broadcast(@NotNull Job<I, O> job, @Nullable I input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <I, O> @NotNull JobDispatch broadcastScheduled(
                @NotNull Job<I, O> job,
                @Nullable I input,
                @NotNull ScheduleKey scheduleKey,
                @NotNull Instant scheduledAt,
                @NotNull OverlapPolicy overlapPolicy) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <I, O> boolean trigger(
                @NotNull Job<I, O> job,
                @Nullable I input,
                @NotNull OverlapPolicy overlapPolicy) {
            unrecorded.countDown();
            return true;
        }

        @Override
        public <I, O> boolean triggerScheduled(
                @NotNull Job<I, O> job,
                @Nullable I input,
                @NotNull ScheduleKey scheduleKey,
                @NotNull Instant scheduledAt,
                @NotNull OverlapPolicy overlapPolicy) {
            unrecorded.countDown();
            return true;
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
        public <O> @NotNull Optional<O> result(@NotNull Id runId, @NotNull Job<?, O> job) {
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
