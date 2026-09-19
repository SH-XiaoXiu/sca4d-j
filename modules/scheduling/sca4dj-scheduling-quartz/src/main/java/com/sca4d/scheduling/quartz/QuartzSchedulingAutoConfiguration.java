package com.sca4d.scheduling.quartz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.job.JobExecutor;
import com.sca4d.core.api.runtime.RuntimeNamespace;
import com.sca4d.core.api.scheduling.ClusterTaskSchedule;
import com.sca4d.core.api.scheduling.JobSchedule;
import com.sca4d.core.api.scheduling.JobScheduler;
import com.sca4d.core.spring.config.RuntimeNamespaceAutoConfiguration;
import com.sca4d.core.spring.json.Sca4djJson;
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
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

/** 自动装配使用主 PostgreSQL DataSource 和应用运行时命名空间的 Quartz 集群调度器。 */
@AutoConfiguration(before = QuartzAutoConfiguration.class, after = RuntimeNamespaceAutoConfiguration.class)
@EnableConfigurationProperties(QuartzSchedulingProperties.class)
public class QuartzSchedulingAutoConfiguration {

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
     * @param runtimeNamespace 生成 Quartz {@code sched_name} 的应用运行时命名空间
     * @return 在 Spring Boot 创建 SchedulerFactoryBean 时应用上述设置的定制器
     * @throws IllegalArgumentException Quartz 配置无效或派生 scheduler 名称超过数据库限制时抛出
     */
    @Bean
    SchedulerFactoryBeanCustomizer sca4djQuartzSchedulerFactoryCustomizer(
            @NotNull DataSource dataSource,
            @NotNull ObjectProvider<JobExecutor> jobExecutor,
            @NotNull QuartzScheduleRegistry registry,
            @NotNull QuartzClusterTaskRegistry clusterTaskRegistry,
            @NotNull QuartzSchedulingProperties properties,
            @NotNull RuntimeNamespace runtimeNamespace) {
        properties.validate();
        String schedulerName = schedulerName(runtimeNamespace);
        return factory -> {
            Properties quartz = new Properties();
            quartz.setProperty("org.quartz.scheduler.instanceName", schedulerName);
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
            factory.setSchedulerName(schedulerName);
            factory.setDataSource(dataSource);
            factory.setQuartzProperties(quartz);
            factory.setAutoStartup(false);
            factory.setWaitForJobsToCompleteOnShutdown(true);
            Map<String, Object> schedulerContext = new HashMap<>();
            schedulerContext.put(QuartzDispatchJob.REGISTRY_CONTEXT_KEY, registry);
            schedulerContext.put(QuartzClusterTaskJob.REGISTRY_CONTEXT_KEY, clusterTaskRegistry);
            JobExecutor executor = jobExecutor.getIfAvailable();
            if (executor != null) {
                schedulerContext.put(QuartzDispatchJob.EXECUTOR_CONTEXT_KEY, executor);
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
     * @param runtimeNamespace 生成 scheduler 名称和对账锁名称的应用运行时命名空间
     * @return 先对账持久化 Job 计划和集群任务、再启动 Quartz 的调度器
     */
    @Bean
    @ConditionalOnMissingBean(JobScheduler.class)
    QuartzJobScheduler quartzJobScheduler(
            @NotNull Scheduler scheduler,
            @NotNull QuartzScheduleRegistry registry,
            @NotNull QuartzClusterTaskRegistry clusterTaskRegistry,
            @NotNull DistributedLock distributedLock,
            @NotNull PlatformTransactionManager transactionManager,
            @NotNull @Sca4djJson ObjectMapper objectMapper,
            @NotNull Clock clock,
            @NotNull ObjectProvider<JobSchedule<?, ?>> schedules,
            @NotNull ObjectProvider<ClusterTaskSchedule> clusterTasks,
            @NotNull RuntimeNamespace runtimeNamespace) {
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
                schedulerName(runtimeNamespace));
    }

    /**
     * 返回当前应用独占的 Quartz JDBC JobStore 名称。
     *
     * @param runtimeNamespace 从 {@code spring.application.name} 创建的运行时命名空间
     * @return 格式为 {@code <app-id>:sca4dj:scheduling:quartz} 的 Quartz scheduler 名称
     * @throws IllegalArgumentException 派生名称超过 Quartz 表 {@code sched_name} 的 120 字符限制时抛出
     */
    private static @NotNull String schedulerName(@NotNull RuntimeNamespace runtimeNamespace) {
        String name = runtimeNamespace.sca4dj("scheduling", "quartz");
        if (name.length() > 120) {
            throw new IllegalArgumentException(
                    "spring.application.name 派生的 Quartz scheduler 名称超过 quartz.qrtz_*"
                            + ".sched_name 的 120 字符限制: length=" + name.length());
        }
        return name;
    }
}
