package com.sca4d.storage.file.lifecycle;

import static java.lang.Boolean.TRUE;

import com.sca4d.core.api.storage.FileStatus;
import com.sca4d.core.api.storage.FileStorage;
import com.sca4d.core.api.storage.StorageObject;
import com.sca4d.storage.file.config.FileProperties;
import com.sca4d.storage.file.persistence.FileRecord;
import com.sca4d.storage.file.persistence.FileRepository;
import com.sca4d.storage.file.transfer.StorageRegistry;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 抢占过期或已释放文件，在事务外幂等删除存储对象并写回结果。 */
public class FileCleanupService {
    private static final Logger LOG = LoggerFactory.getLogger(FileCleanupService.class);

    private final @NotNull FileRepository repository;

    private final @NotNull StorageRegistry storages;

    private final @NotNull TransactionTemplate transactions;

    private final @NotNull Clock clock;

    private final @NotNull FileProperties properties;

    private final @NotNull String owner = UUID.randomUUID().toString();

    /**
     * 创建文件清理操作。
     *
     * @param repository 文件状态、删除租约和结果的持久化入口
     * @param storages 按 backendId 选择文件存储的注册表
     * @param transactionManager 主 PostgreSQL 事务管理器
     * @param clock 判断到期和计算重试时间的时钟
     * @param properties 文件保留期、删除租约和重试间隔配置
     */
    public FileCleanupService(
            @NotNull FileRepository repository,
            @NotNull StorageRegistry storages,
            @NotNull PlatformTransactionManager transactionManager,
            @NotNull Clock clock,
            @NotNull FileProperties properties) {
        this.repository = Objects.requireNonNull(repository, "文件 Repository 不能为空");
        this.storages = Objects.requireNonNull(storages, "存储注册表不能为空");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.properties = Objects.requireNonNull(properties, "文件配置不能为空");
    }

    /** 清理到期临时对象以及等待删除的正式对象。 */
    public void cleanup() {
        // 先在短事务中把超过保留期的文件转换为待删除状态，并读取上传未完成的临时对象。
        Instant now = clock.instant();
        transactions.executeWithoutResult(status -> repository.requestExpiredDeletion(now));
        List<FileRecord> temporary = Objects.requireNonNull(
                transactions.execute(status -> repository.findExpiredUploadObjects(now)),
                "过期上传对象查询结果不能为空");
        for (FileRecord record : temporary) {
            cleanupTemporary(record);
        }

        // 领取正式删除租约后在事务外访问对象存储，单个文件失败不阻止同一批次的其他文件。
        List<FileRecord> claimed = Objects.requireNonNull(
                transactions.execute(
                        status -> repository.claimDeletion(now, now.plus(properties.getDeleteLease()), owner)),
                "待删除文件领取结果不能为空");
        for (FileRecord record : claimed) {
            delete(record);
        }

        if (!temporary.isEmpty() || !claimed.isEmpty()) {
            LOG.debug(
                    "文件清理批次完成 | temporaryCount={} claimedCount={}",
                    temporary.size(),
                    claimed.size());
        }
    }

    /** 删除上传签名到期后的临时对象，并记录临时对象已经清理。 */
    private void cleanupTemporary(@NotNull FileRecord record) {
        try {
            storages.storage(record.backendId())
                    .delete(
                            new StorageObject(record.containerName(), record.uploadObjectKey()));
            transactions.executeWithoutResult(status -> repository.markUploadObjectCleaned(record.id()));
        } catch (RuntimeException exception) {
            LOG.warn(
                    "上传临时对象清理失败，稍后重试 | fileId={} backendId={} errorType={}",
                    record.id(),
                    record.backendId(),
                    exception.getClass().getSimpleName());
        }
    }

    /** 删除领取到的临时对象和正式对象，并写回成功或重试时间。 */
    private void delete(@NotNull FileRecord record) {
        try {
            FileStorage storage = storages.storage(record.backendId());
            storage.delete(new StorageObject(record.containerName(), record.uploadObjectKey()));
            String objectKey = record.objectKey();
            if (objectKey != null) {
                storage.delete(new StorageObject(record.containerName(), objectKey));
            }
            boolean recorded = TRUE.equals(
                    transactions.execute(
                            status -> repository.saveDeletionResult(record.id(), owner, FileStatus.DELETED, null)));
            if (recorded) {
                LOG.info("文件对象已删除 | fileId={} backendId={}", record.id(), record.backendId());
            } else {
                LOG.debug(
                        "文件删除租约已经转移，忽略本次成功结果 | fileId={} backendId={}",
                        record.id(),
                        record.backendId());
            }
        } catch (RuntimeException exception) {
            Instant retryAt = clock.instant().plus(properties.getDeleteRetryDelay());
            boolean recorded = TRUE.equals(
                    transactions.execute(
                            status -> repository
                                    .saveDeletionResult(record.id(), owner, FileStatus.DELETE_FAILED, retryAt)));
            if (recorded) {
                LOG.warn(
                        "文件对象删除失败，稍后重试 | fileId={} backendId={} errorType={}",
                        record.id(),
                        record.backendId(),
                        exception.getClass().getSimpleName());
            } else {
                LOG.debug(
                        "文件删除租约已经转移，忽略本次失败结果 | fileId={} backendId={}",
                        record.id(),
                        record.backendId());
            }
        }
    }
}
