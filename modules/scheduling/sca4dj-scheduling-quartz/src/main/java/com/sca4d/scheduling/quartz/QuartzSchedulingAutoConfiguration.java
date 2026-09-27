package com.sca4d.scheduling.quartz;

import static com.sca4d.core.api.name.NameFormat.COLON;
import static com.sca4d.scheduling.quartz.QuartzDispatchJob.EXECUTOR_CONTEXT_KEY;

import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.identity.SystemPrincipals;
import com.sca4d.core.api.job.JobExecutor;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.runtime.InstanceCapability;
import com.sca4d.core.api.scheduling.ClusterTaskSchedule;
import com.sca4d.core.api.scheduling.JobSchedule;
import com.sca4d.core.api.scheduling.JobScheduler;
import com.sca4d.core.spring.config.AppIsolationAutoConfiguration;
import com.sca4d.core.spring.config.ConditionalOnCapability;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.json.Sca4djMapper;
import org.jetbrains.annotations.NotNull;
import org.quartz.Scheduler;
import org.quartz.impl.jdbcjobstore.PostgreSQLDelegate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.quartz.LocalDataSourceJobStore;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

/** 自动装配使用主 PostgreSQL DataSource 和 App 隔离名称的 Quartz 集群调度器。 */
@AutoConfiguration(before = QuartzAutoConfiguration.class, after = AppIsolationAutoConfiguration.class)
@EnableConfigurationProperties(QuartzSchedulingProperties.class)
public class QuartzSchedulingAutoConfiguration {

    /** 集群调度按代码中声明的完整计划运行，实例可以独立选择是否运行调度线程。 */
    @Bean
    InstanceCapability schedulingWorkerCapability() {
        return new InstanceCapability(TreeCode.ofColon("SCHEDULING:WORKER"), SystemPrincipals.MAIN, List.of());
    }

    /** 创建 Quartz 调度模块自动配置。 */
    public QuartzSchedulingAutoConfiguration() {
    }

    /**
     * 创建只保存当前代码计划定义的进程内目录。
     *
     * @return 保存对账成功定义并供 Quartz fire 恢复计划的进程内目录
     */
    @Bean
    QuartzScheduleRegistry quartzScheduleRegistry() {
        return new QuartzScheduleRegistry();
    }

    /**
     * 创建只保存当前集群任务定义的进程内目录。
     *
     * @return 保存对账成功定义并供 Quartz fire 恢复任务的进程内目录
     */
    @Bean
    QuartzClusterTaskRegistry quartzClusterTaskRegistry() {
        return new QuartzClusterTaskRegistry();
    }

    /**
     * 配置 Quartz 使用主 PostgreSQL、{@code quartz.qrtz_} 表和集群 JobStore。
     *
     * @param dataSource 主 PostgreSQL DataSource
     * @param jobExecutor Quartz fire 提交持久化 Job run 的可选入口
     * @param registry 对账成功后保存可执行计划的目录
     * @param clusterTaskRegistry 对账成功后保存可执行集群任务的目录
     * @param properties Quartz 工作线程和集群签到配置
     * @return 在 Spring Boot 创建 SchedulerFactoryBean 时应用前述设置的定制器
     * @throws IllegalArgumentException Quartz 配置无效或派生 scheduler 名称超过数据库限制时抛出
     */
    @Bean
    SchedulerFactoryBeanCustomizer sca4djQuartzSchedulerFactoryCustomizer(
            @NotNull DataSource dataSource,
            @NotNull ObjectProvider<JobExecutor> jobExecutor,
            @NotNull QuartzScheduleRegistry registry,
            @NotNull QuartzClusterTaskRegistry clusterTaskRegistry,
            @NotNull QuartzSchedulingProperties properties) {
        properties.validate();
        Key schedulerName = schedulerName();
        return factory -> {
            Properties quartz = new Properties();
            quartz.setProperty("org.quartz.scheduler.instanceName", schedulerName.format(COLON));
            quartz.setProperty("org.quartz.scheduler.instanceId", "AUTO");
            quartz.setProperty("org.quartz.threadPool.threadCount", Integer.toString(properties.getThreadCount()));
            quartz.setProperty("org.quartz.jobStore.class", LocalDataSourceJobStore.class.getName());
            quartz.setProperty("org.quartz.jobStore.driverDelegateClass", PostgreSQLDelegate.class.getName());
            quartz.setProperty("org.quartz.jobStore.tablePrefix", "quartz.qrtz_");
            quartz.setProperty("org.quartz.jobStore.isClustered", "true");
            quartz.setProperty("org.quartz.jobStore.useProperties", "true");
            quartz.setProperty(
                    "org.quartz.jobStore.clusterCheckinInterval",
                    Long.toString(properties.getClusterCheckinInterval().toMillis()));
            factory.setSchedulerName(schedulerName.format(COLON));
            factory.setDataSource(dataSource);
            factory.setQuartzProperties(quartz);
            factory.setAutoStartup(false);
            factory.setWaitForJobsToCompleteOnShutdown(true);
            Map<String, Object> schedulerContext = new HashMap<>();
            schedulerContext.put(QuartzDispatchJob.REGISTRY_CONTEXT_KEY, registry);
            schedulerContext.put(QuartzClusterTaskJob.REGISTRY_CONTEXT_KEY, clusterTaskRegistry);
            JobExecutor executor = jobExecutor.getIfAvailable();
            if (executor != null) {
                schedulerContext.put(EXECUTOR_CONTEXT_KEY, executor);
            }
            factory.setSchedulerContextAsMap(Map.copyOf(schedulerContext));
        };
    }

    /**
     * 创建使用 Quartz 对账代码计划并定时提交 Job 的调度器。
     *
     * @param scheduler Spring Boot 创建但尚未自动启动的 Quartz Scheduler
     * @param registry 对账成功后供 fire 查询的计划目录
     * @param clusterTaskRegistry 对账成功后供 fire 查询的集群任务目录
     * @param distributedLock Quartz 计划对账使用的 Redis 分布式锁
     * @param transactionManager 主 PostgreSQL 事务管理器
     * @param objectMapper 脚手架统一 JSON 映射器
     * @param clock 固定间隔计划计算首次触发时间的时钟
     * @param schedules 当前应用声明的全部 JobSchedule Bean
     * @param clusterTasks 当前应用声明的全部 ClusterTaskSchedule Bean
     * @return 先对账持久化 Job 计划和集群任务、再启动 Quartz 的调度器
     */
    @Bean
    @ConditionalOnMissingBean(JobScheduler.class)
    @ConditionalOnCapability("SCHEDULING:WORKER")
    QuartzJobScheduler quartzJobScheduler(
            @NotNull Scheduler scheduler,
            @NotNull QuartzScheduleRegistry registry,
            @NotNull QuartzClusterTaskRegistry clusterTaskRegistry,
            @NotNull DistributedLock distributedLock,
            @NotNull PlatformTransactionManager transactionManager,
            @NotNull @Sca4djJson Sca4djMapper objectMapper,
            @NotNull Clock clock,
            @NotNull ObjectProvider<JobSchedule<?, ?>> schedules,
            @NotNull ObjectProvider<ClusterTaskSchedule> clusterTasks) {
        return new QuartzJobScheduler(
                scheduler,
                registry,
                clusterTaskRegistry,
                distributedLock,
                transactionManager,
                objectMapper,
                clock,
                schedules,
                clusterTasks,
                schedulerName());
    }

    /**
     * 返回当前应用独占的 Quartz JDBC JobStore 名称。
     *
     * @return 格式为 {@code <app-id>:sca4dj:scheduling:quartz} 的 Quartz scheduler 名称
     * @throws IllegalArgumentException 派生名称超过 Quartz 表 {@code sched_name} 的 120 字符限制时抛出
     */
    private static @NotNull Key schedulerName() {
        Key name = Key.of(TreeCode.ofColon("SCHEDULING:QUARTZ"));
        if (name.format(COLON).length() > 120) {
            throw new IllegalArgumentException(
                    "sca4dj.io.key-prefix 派生的 Quartz scheduler 名称超过 quartz.qrtz_*"
                            + ".sched_name 的 120 字符限制: length=" + name.format(COLON).length());
        }
        return name;
    }
}
