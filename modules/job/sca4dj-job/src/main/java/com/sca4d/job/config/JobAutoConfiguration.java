package com.sca4d.job.config;

import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.identity.SystemPrincipals;
import com.sca4d.core.api.job.DirectJob;
import com.sca4d.core.api.job.Job;
import com.sca4d.core.api.job.JobExecutor;
import com.sca4d.core.api.job.JobHistory;
import com.sca4d.core.api.job.JobKey;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.runtime.InstanceCapabilities;
import com.sca4d.core.api.runtime.InstanceCapability;
import com.sca4d.core.api.scheduling.IntervalSchedule;
import com.sca4d.core.api.scheduling.JobSchedule;
import com.sca4d.core.api.scheduling.MisfirePolicy;
import com.sca4d.core.api.scheduling.OverlapPolicy;
import com.sca4d.core.api.scheduling.ScheduleKey;
import com.sca4d.core.spring.config.AppIsolationAutoConfiguration;
import com.sca4d.core.spring.config.CoreSpringAutoConfiguration;
import com.sca4d.core.spring.config.InstanceProperties;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.json.Sca4djMapper;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.time.Clock;
import java.util.List;

/** 自动装配使用主 PostgreSQL DataSource 的 Job 定义目录、持久化执行器和集群 worker。 */
@AutoConfiguration(after = {CoreSpringAutoConfiguration.class, AppIsolationAutoConfiguration.class})
@EnableConfigurationProperties(JobProperties.class)
@EnableTransactionManagement
@MapperScan(basePackageClasses = JobMapper.class)
public class JobAutoConfiguration {

    /** Job 工作线程属于 MAIN 自主身份，启用选择不影响 Job 提交入口。 */
    @Bean
    InstanceCapability jobWorkerCapability() {
        return new InstanceCapability(TreeCode.ofColon("JOB:WORKER"), SystemPrincipals.MAIN, List.of());
    }

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
    JobCatalog jobCatalog(@NotNull ObjectProvider<Job<?, ?>> jobs) {
        return new JobCatalog(() -> jobs.orderedStream().toList());
    }

    /**
     * 创建按 App 隔离名称读写 Job 运行数据的 Repository。
     *
     * @param mapper Job 表的 SQL 读写
     * @return 隐藏 Mapper 和 App 隔离名称的 Job Repository
     */
    @Bean
    JobRepository jobRepository(
            @NotNull JobMapper mapper,
            @NotNull SqlBatches sqlBatches) {
        return new JobRepository(mapper, sqlBatches);
    }

    /** 创建 JobRunService，由它在各自事务中修改 Job run、节点、依赖和投递记录。 */
    @Bean
    JobRunService jobRunService(
            @NotNull JobRepository repository,
            @NotNull PlatformTransactionManager transactionManager,
            @NotNull Clock clock,
            @NotNull DistributedLock distributedLock,
            @NotNull @Sca4djJson Sca4djMapper objectMapper) {
        return new JobRunService(repository, transactionManager, clock, distributedLock, objectMapper);
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

    /** 创建只读、限定当前 App 隔离名称的类型化 Job 历史查询。 */
    @Bean
    @ConditionalOnMissingBean(JobHistory.class)
    JobHistory jobHistory(
            @NotNull JobRepository repository,
            @NotNull @Sca4djJson Sca4djMapper objectMapper) {
        return new DefaultJobHistory(repository, objectMapper);
    }

    /** 创建按配置间隔执行且不生成 Job run 的成功历史清理计划。 */
    @Bean
    JobSchedule<Void, Void> jobHistoryCleanupSchedule(
            @Qualifier("jobHistoryCleanupJob") @NotNull DirectJob<Void, Void> job,
            @NotNull JobProperties properties) {
        return new JobSchedule<>(
                new ScheduleKey("JOB/HISTORY/CLEANUP"),
                1,
                job,
                null,
                new IntervalSchedule(
                        properties.getHistoryCleanupInterval(),
                        properties.getHistoryCleanupInterval()),
                MisfirePolicy.FIRE_ONCE_NOW,
                OverlapPolicy.SKIP_IF_RUNNING);
    }

    /** 登记不保存 Run 的 Job 历史清理工作。 */
    @Bean
    DirectJob<Void, Void> jobHistoryCleanupJob(@NotNull JobHistoryCleanupTask task) {
        return DirectJob.unrecorded(
                new JobKey("JOB/HISTORY/CLEANUP"),
                1,
                TreeCode.ofColon("JOB:WORKER"),
                task::execute);
    }

    /**
     * 创建供调用方同步执行或异步提交 Job 的默认执行器。
     *
     * @param catalog 当前应用 Job 定义目录
     * @param runs 当前 App 隔离名称的 Job 运行服务
     * @param objectMapper 脚手架 JSON 映射器
     * @return 默认持久化 JobExecutor；应用声明自己的 JobExecutor 时不创建
     */
    @Bean
    @ConditionalOnMissingBean(JobExecutor.class)
    JobExecutor jobExecutor(
            @NotNull JobCatalog catalog,
            @NotNull JobRunService runs,
            @NotNull @Sca4djJson Sca4djMapper objectMapper,
            @NotNull InstanceCapabilities instanceCapabilities,
            @NotNull InstanceProperties instanceProperties,
            @NotNull DistributedLock distributedLock,
            @NotNull Clock clock,
            @NotNull JobProperties properties) {
        return new DefaultJobExecutor(
                catalog,
                runs,
                objectMapper,
                instanceCapabilities,
                instanceProperties,
                distributedLock,
                clock,
                properties);
    }

    /**
     * 创建领取并执行持久化 Job 节点的集群 worker。
     *
     * @param catalog 当前应用 Job 定义目录
     * @param runs 当前 App 隔离名称的 Job 运行服务
     * @param objectMapper 脚手架 JSON 映射器
     * @param properties worker、轮询和租约配置
     * @param clock 记录节点执行时间使用的时钟
     * @return 随 Spring 容器启动和停止的集群 worker
     * @throws IllegalArgumentException worker、轮询或租约配置无效时抛出
     */
    @Bean
    JobWorker jobWorker(
            @NotNull JobCatalog catalog,
            @NotNull JobRunService runs,
            @NotNull @Sca4djJson Sca4djMapper objectMapper,
            @NotNull JobProperties properties,
            @NotNull Clock clock,
            @NotNull InstanceProperties instanceProperties,
            @NotNull InstanceCapabilities instanceCapabilities) {
        properties.validate();
        return new JobWorker(
                catalog,
                runs,
                objectMapper,
                properties,
                clock,
                instanceProperties,
                instanceCapabilities);
    }
}
