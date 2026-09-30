package com.sca4d.storage.file.config;

import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.job.JobExecutor;
import com.sca4d.core.api.scheduling.IntervalSchedule;
import com.sca4d.core.api.scheduling.JobSchedule;
import com.sca4d.core.api.scheduling.JobScheduler;
import com.sca4d.core.api.scheduling.MisfirePolicy;
import com.sca4d.core.api.scheduling.OverlapPolicy;
import com.sca4d.core.api.scheduling.ScheduleKey;
import com.sca4d.core.api.security.SecretKeyring;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.access.SettingWriter;
import com.sca4d.core.api.setting.definition.SystemSetting;
import com.sca4d.core.api.storage.FileService;
import com.sca4d.core.api.storage.FileStorage;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.storage.file.lifecycle.DefaultFileService;
import com.sca4d.storage.file.lifecycle.FileCleanupJob;
import com.sca4d.storage.file.lifecycle.FileCleanupService;
import com.sca4d.storage.file.persistence.FileRepository;
import com.sca4d.storage.file.persistence.mapper.FileMapper;
import com.sca4d.storage.file.transfer.StorageRegistry;
import com.sca4d.storage.file.transfer.TransferTokenService;
import org.jetbrains.annotations.NotNull;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** 自动装配文件数据库状态、存储选择、签名传输和清理任务。 */
@AutoConfiguration
@MapperScan(basePackageClasses = FileMapper.class)
@EnableConfigurationProperties(FileProperties.class)
@EnableTransactionManagement
public class FileAutoConfiguration {

    /** 创建本应用上下文的模块设置，来源由 Spring 显式注入。 */
    @Bean
    FileSettings fileSettings(SettingReader<String> reader, SettingWriter<String> writer) {
        return new FileSettings(reader, writer);
    }

    public FileAutoConfiguration() {
    }

    /** @return 文件上传有效期设置 */
    @Bean
    SystemSetting<Duration> fileUploadTtlSetting(FileSettings settings) {
        return settings.UPLOAD_TTL;
    }

    /** @return 文件访问地址有效期设置 */
    @Bean
    SystemSetting<Duration> fileAccessTtlSetting(FileSettings settings) {
        return settings.ACCESS_TTL;
    }

    /** @return 待绑定文件保留期设置 */
    @Bean
    SystemSetting<Duration> fileReadyRetentionSetting(FileSettings settings) {
        return settings.READY_RETENTION;
    }

    /** @return 文件大小上限设置 */
    @Bean
    SystemSetting<Long> fileMaximumSizeSetting(FileSettings settings) {
        return settings.MAXIMUM_SIZE_BYTES;
    }

    /**
     * 创建读写文件元数据的 Repository。
     *
     * @param mapper 直接读写文件表的 Mapper
     * @return 隐藏 FileMapper 和数据库结果结构的文件持久化入口
     */
    @Bean
    FileRepository fileRepository(@NotNull FileMapper mapper, @NotNull SqlBatches sqlBatches) {
        return new FileRepository(mapper, sqlBatches);
    }

    /**
     * 创建按 backend ID 选择 FileStorage 的注册表。
     *
     * @param storages 当前应用安装的全部文件存储实现
     * @param properties 默认存储 backend 配置
     * @return 已校验默认 backend 和重复 backend ID 的存储注册表
     */
    @Bean
    StorageRegistry storageRegistry(
            @NotNull List<@NotNull FileStorage> storages,
            @NotNull FileProperties properties) {
        return new StorageRegistry(storages, properties.getDefaultBackend());
    }

    @Bean
    TransferTokenService storageTransferTokenService(
            @NotNull SecretKeyring keyring,
            @NotNull FileProperties properties,
            @NotNull Clock storageClock) {
        return new TransferTokenService(keyring, properties.getTransferBaseUrl(), storageClock);
    }

    @Bean
    FileService fileService(
            @NotNull FileRepository repository,
            @NotNull StorageRegistry storages,
            @NotNull IdGenerator ids,
            @NotNull FileProperties properties,
            @NotNull FileSettings settings,
            @NotNull TransferTokenService tokens,
            @NotNull Clock storageClock,
            @NotNull DistributedLock distributedLock,
            @NotNull PlatformTransactionManager transactions) {
        return new DefaultFileService(
                repository,
                storages,
                ids,
                properties,
                settings,
                tokens,
                storageClock,
                distributedLock,
                transactions);
    }

    /**
     * 创建保留文件级删除租约和重试语义的清理操作。
     *
     * @param repository 文件状态持久化入口
     * @param storages 按 backend ID 选择实际存储的注册表
     * @param transactions 文件清理事务管理器
     * @param storageClock 文件状态和租约使用的时钟
     * @param properties 文件保留期、租约和重试配置
     * @return 执行一次文件清理扫描的操作
     */
    @Bean
    FileCleanupService fileCleanupService(
            @NotNull FileRepository repository,
            @NotNull StorageRegistry storages,
            @NotNull PlatformTransactionManager transactions,
            @NotNull Clock storageClock,
            @NotNull FileProperties properties) {
        return new FileCleanupService(repository, storages, transactions, storageClock, properties);
    }

    /**
     * 创建调用 FileCleanupService 执行一次清理扫描的 DirectJob。
     *
     * @param cleanupService 保留文件级租约和失败恢复语义的清理操作
     * @return 文件清理 Job 定义
     */
    @Bean
    FileCleanupJob fileCleanupJob(@NotNull FileCleanupService cleanupService) {
        return new FileCleanupJob(cleanupService);
    }

    /**
     * 创建按文件清理间隔提交 FileCleanupJob 的代码计划。
     *
     * @param job 每次触发时提交的文件清理 Job
     * @param properties 文件清理间隔配置
     * @param jobExecutor 应用安装的持久化 Job 提交入口
     * @param jobScheduler 应用安装的代码计划调度器
     * @return 按文件清理间隔提交且禁止同计划 run 重叠的代码计划
     */
    @Bean
    JobSchedule<Void, Void> fileCleanupSchedule(
            @NotNull FileCleanupJob job,
            @NotNull FileProperties properties,
            @NotNull JobExecutor jobExecutor,
            @NotNull JobScheduler jobScheduler) {
        Objects.requireNonNull(jobExecutor, "文件清理计划要求应用提供 JobExecutor Bean");
        Objects.requireNonNull(jobScheduler, "文件清理计划要求应用提供 JobScheduler Bean");
        return new JobSchedule<>(
                new ScheduleKey("STORAGE/FILE/CLEANUP"),
                1,
                job,
                null,
                new IntervalSchedule(properties.getCleanupInterval(), properties.getCleanupInterval()),
                MisfirePolicy.FIRE_ONCE_NOW,
                OverlapPolicy.SKIP_IF_RUNNING);
    }
}
