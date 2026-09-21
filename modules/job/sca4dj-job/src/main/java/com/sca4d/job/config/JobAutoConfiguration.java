package com.sca4d.job.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.job.Job;
import com.sca4d.core.api.job.JobExecutor;
import com.sca4d.core.api.job.JobHistory;
import com.sca4d.core.api.runtime.RuntimeNamespace;
import com.sca4d.core.api.scheduling.ClusterTaskSchedule;
import com.sca4d.core.api.scheduling.IntervalSchedule;
import com.sca4d.core.api.scheduling.MisfirePolicy;
import com.sca4d.core.api.scheduling.ScheduleKey;
import com.sca4d.core.spring.config.CoreSpringAutoConfiguration;
import com.sca4d.core.spring.config.RuntimeNamespaceAutoConfiguration;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.job.persistence.JobMapper;
import com.sca4d.job.persistence.JobRepository;
import com.sca4d.job.runtime.DefaultJobExecutor;
import com.sca4d.job.runtime.DefaultJobHistory;
import com.sca4d.job.runtime.JobCatalog;
import com.sca4d.job.runtime.JobHistoryCleanupTask;
import com.sca4d.job.runtime.JobHistoryService;
import com.sca4d.job.runtime.JobRunService;
import com.sca4d.job.runtime.JobWorker;
import org.jetbrains.annotations.NotNull;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.time.Clock;
import java.util.List;

/** 自动装配使用主 PostgreSQL DataSource 的 Job 定义目录、持久化执行器和集群 worker。 */
@AutoConfiguration(after = {CoreSpringAutoConfiguration.class, RuntimeNamespaceAutoConfiguration.class})
@EnableConfigurationProperties(JobProperties.class)
@EnableTransactionManagement
@MapperScan(basePackageClasses = JobMapper.class)
public class JobAutoConfiguration {

    /** 创建 Job 模块自动配置。 */
    public JobAutoConfiguration() {
    }

    /**
     * 收集并校验当前应用声明的全部 Job。
     *
     * @param jobs 当前应用声明的全部 Job Bean
     * @return 已校验 key、定义版本和 PipelineJob 计划的定义目录
     * @throws IllegalStateException Job key 重复、定义版本无效或 PipelineJob 计划无效时抛出
     */
    @Bean
    JobCatalog jobCatalog(@NotNull List<@NotNull Job<?, ?>> jobs) {
        return new JobCatalog(jobs);
    }

    /**
     * 创建按运行时命名空间读写 Job 运行数据的 Repository。
     *
     * @param mapper Job 表的 SQL 读写
     * @param runtimeNamespace 隔离同一 PostgreSQL 中不同应用 Job 数据的运行时命名空间
     * @return 隐藏 Mapper 和运行时命名空间的 Job Repository
     */
    @Bean
    JobRepository jobRepository(
            @NotNull JobMapper mapper,
            @NotNull RuntimeNamespace runtimeNamespace,
            @NotNull SqlBatches sqlBatches) {
        return new JobRepository(mapper, runtimeNamespace, sqlBatches);
    }

    /** 创建编排 Job 状态变化并持有事务边界的运行服务。 */
    @Bean
    JobRunService jobRunService(
            @NotNull JobRepository repository,
            @NotNull PlatformTransactionManager transactionManager,
            @NotNull Clock clock,
            @NotNull DistributedLock distributedLock) {
        return new JobRunService(repository, transactionManager, clock, distributedLock);
    }

    /** 创建按配置窗口清理成功 Job run 的历史服务。 */
    @Bean
    JobHistoryService jobHistoryService(
            @NotNull JobRepository repository,
            @NotNull JobProperties properties,
            @NotNull Clock clock) {
        return new JobHistoryService(repository, properties, clock);
    }

    /** 创建由集群定时器直接触发的 Job 成功历史清理任务。 */
    @Bean
    JobHistoryCleanupTask jobHistoryCleanupTask(@NotNull JobHistoryService history) {
        return new JobHistoryCleanupTask(history);
    }

    /** 创建只读、限定当前应用命名空间的类型化 Job 历史查询。 */
    @Bean
    @ConditionalOnMissingBean(JobHistory.class)
    JobHistory jobHistory(
            @NotNull JobRepository repository,
            @NotNull @Sca4djJson ObjectMapper objectMapper) {
        return new DefaultJobHistory(repository, objectMapper);
    }

    /** 创建按配置间隔执行且不生成 Job run 的成功历史清理计划。 */
    @Bean
    ClusterTaskSchedule jobHistoryCleanupSchedule(
            @NotNull JobHistoryCleanupTask task,
            @NotNull JobProperties properties) {
        return new ClusterTaskSchedule(
                new ScheduleKey("JOB/HISTORY/CLEANUP"),
                1,
                task,
                new IntervalSchedule(
                        properties.getHistoryCleanupInterval(),
                        properties.getHistoryCleanupInterval()),
                MisfirePolicy.FIRE_ONCE_NOW);
    }

    /**
     * 创建供调用方同步执行或异步提交 Job 的默认执行器。
     *
     * @param catalog 当前应用 Job 定义目录
     * @param runs 当前运行时命名空间的 Job 运行服务
     * @param objectMapper 脚手架统一 JSON 映射器
     * @return 默认持久化 JobExecutor；应用声明自己的 JobExecutor 时不创建
     */
    @Bean
    @ConditionalOnMissingBean(JobExecutor.class)
    JobExecutor jobExecutor(
            @NotNull JobCatalog catalog,
            @NotNull JobRunService runs,
            @NotNull @Sca4djJson ObjectMapper objectMapper) {
        return new DefaultJobExecutor(catalog, runs, objectMapper);
    }

    /**
     * 创建领取并执行持久化 Job 节点的集群 worker。
     *
     * @param catalog 当前应用 Job 定义目录
     * @param runs 当前运行时命名空间的 Job 运行服务
     * @param objectMapper 脚手架统一 JSON 映射器
     * @param properties worker、轮询和租约配置
     * @param clock 节点执行事实使用的时钟
     * @param runtimeNamespace 日志和共享 Job 表使用的应用运行时命名空间
     * @return 随 Spring 生命周期启动和停止的集群 worker
     * @throws IllegalArgumentException worker、轮询或租约配置无效时抛出
     */
    @Bean
    JobWorker jobWorker(
            @NotNull JobCatalog catalog,
            @NotNull JobRunService runs,
            @NotNull @Sca4djJson ObjectMapper objectMapper,
            @NotNull JobProperties properties,
            @NotNull Clock clock,
            @NotNull RuntimeNamespace runtimeNamespace) {
        properties.validate();
        return new JobWorker(catalog, runs, objectMapper, properties, clock, runtimeNamespace);
    }
}
