package com.sca4d.storage.file.lifecycle;

import static com.sca4d.core.api.name.NameFormat.SLASH;
import static java.lang.Boolean.TRUE;

import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.resource.Resource;
import com.sca4d.core.api.setting.value.SettingValues;
import com.sca4d.core.api.storage.FileAccessUrl;
import com.sca4d.core.api.storage.FileContent;
import com.sca4d.core.api.storage.FileErrorCode;
import com.sca4d.core.api.storage.FileService;
import com.sca4d.core.api.storage.FileStatus;
import com.sca4d.core.api.storage.FileStorage;
import com.sca4d.core.api.storage.FileUpload;
import com.sca4d.core.api.storage.FileUploadIntent;
import com.sca4d.core.api.storage.FileUse;
import com.sca4d.core.api.storage.StorageObject;
import com.sca4d.core.api.storage.StorageObjectMetadata;
import com.sca4d.core.api.storage.StorageUpload;
import com.sca4d.core.api.storage.StoredFile;
import com.sca4d.storage.file.config.FileProperties;
import com.sca4d.storage.file.config.FileSettings;
import com.sca4d.storage.file.persistence.FileRecord;
import com.sca4d.storage.file.persistence.FileRepository;
import com.sca4d.storage.file.transfer.StorageRegistry;
import com.sca4d.storage.file.transfer.TransferTokenService;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 使用 {@link FileRecord} 协调文件上传、正式对象读取、业务绑定和异步删除登记。
 *
 * <p>本服务保存文件记录并推进 {@link FileStatus}，不判断 App 的业务权限。调用方必须先完成目标 Resource 的授权，
 * 再把已经确认的 {@link FileUse} 传入本服务。对象存储读写失败时，方法返回稳定的 {@link FileErrorCode}，不会把
 * backend、container、对象键、传输 Token 或访问地址暴露给调用方。存储实现的运行时故障转换为
 * 稳定的 {@link FileErrorCode}；内容流读写失败仍按 {@link FileService} 契约抛出 {@link IOException}。
 */
public class DefaultFileService implements FileService {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultFileService.class);

    /**
     * 正式对象键使用的 UTC 年月目录格式。
     */
    private static final DateTimeFormatter OBJECT_MONTH = DateTimeFormatter.ofPattern("uuuu/MM")
            .withZone(ZoneOffset.UTC);

    /**
     * 同一文件的上传写入和完成确认等待分布式锁的最长时间。
     */
    private static final Duration FILE_OPERATION_LOCK_WAIT = Duration.ofSeconds(10);

    private final @NotNull TransferTokenService transferTokens;

    private final @NotNull FileRepository repository;

    private final @NotNull StorageRegistry storages;

    private final @NotNull FileProperties properties;

    private final @NotNull Clock clock;

    private final @NotNull DistributedLock distributedLock;

    private final @NotNull FileSettings settings;

    private final @NotNull IdGenerator ids;

    private final @NotNull TransactionTemplate transactions;

    /**
     * 创建使用数据库事务、分布式锁和已注册 {@link FileStorage} 的文件服务。
     *
     * <p>对象存储操作不占用数据库事务；需要在对象存储操作前后读写状态的方法通过短事务完成数据库操作。
     */
    public DefaultFileService(
            @NotNull FileRepository repository,
            @NotNull StorageRegistry storages,
            @NotNull IdGenerator ids,
            @NotNull FileProperties properties,
            @NotNull FileSettings settings,
            @NotNull TransferTokenService transferTokens,
            @NotNull Clock clock,
            @NotNull DistributedLock distributedLock,
            @NotNull PlatformTransactionManager transactionManager) {
        this.repository = Objects.requireNonNull(repository, "文件 Repository 不能为空");
        this.storages = Objects.requireNonNull(storages, "存储实现注册表不能为空");
        this.ids = Objects.requireNonNull(ids, "标识生成器不能为空");
        this.properties = Objects.requireNonNull(properties, "文件配置不能为空");
        this.settings = Objects.requireNonNull(settings, "Settings 不能为空");
        this.transferTokens = Objects.requireNonNull(transferTokens, "文件传输签名器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.distributedLock = Objects.requireNonNull(distributedLock, "分布式锁不能为空");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
    }

    @Override
    @CheckReturnValue
    public @NotNull FileUpload createUpload(
            @NotNull Principal uploader,
            @NotNull FileUploadIntent intent) {
        FileRecord record = createPending(uploader, intent);
        FileStorage storage = storages.storage(record.backendId());

        // 数据库事务结束后签发上传请求；签发失败时 PENDING 记录到期后由清理任务删除。
        // 日志不记录 URL、请求头或对象键。
        StorageUpload upload;
        try {
            upload = storage.prepareUpload(
                    record.id(),
                    uploadObject(record),
                    record.declaredContentType(),
                    record.declaredSizeBytes(),
                    record.uploadExpiresAt());
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LOG.warn(
                    "文件存储无法签发上传请求 | fileId={} backendId={} errorType={}",
                    record.id(),
                    record.backendId(),
                    exception.getClass().getSimpleName());
            throw new BusinessException(FileErrorCode.BACKEND_UNAVAILABLE, exception);
        }
        LOG.debug(
                "客户端文件上传已申请 | fileId={} principalId={} backendId={} scene={} targetType={} targetId={} sizeBytes={}",
                record.id(),
                record.uploaderPrincipalId(),
                record.backendId(),
                record.scene(),
                record.targetResourceType(),
                record.targetResourceId(),
                record.declaredSizeBytes());
        return new FileUpload(
                record.id(),
                "PUT",
                upload.url(),
                upload.headers(),
                record.uploadExpiresAt());
    }

    @Override
    @CheckReturnValue
    public @NotNull StoredFile upload(
            @NotNull Principal uploader,
            @NotNull FileUploadIntent intent,
            @NotNull InputStream content)
            throws IOException {
        InputStream checkedContent = Objects.requireNonNull(content, "文件内容不能为空");
        FileRecord created = createPending(uploader, intent);
        LOG.trace(
                "服务端文件上传等待文件锁 | fileId={} principalId={} sizeBytes={}",
                created.id(),
                created.uploaderPrincipalId(),
                created.declaredSizeBytes());
        try {
            return distributedLock.execute(fileOperationLock(created.id()), FILE_OPERATION_LOCK_WAIT, () -> {
                FileRecord record = Objects.requireNonNull(
                        transactions.execute(status -> find(created.id())),
                        "文件读取事务没有返回结果");
                if (record.status() != FileStatus.PENDING) {
                    LOG.trace("文件状态不允许服务端上传 | fileId={} status={}", record.id(), record.status());
                    throw new BusinessException(FileErrorCode.STATE_CONFLICT);
                }
                if (!clock.instant().isBefore(record.uploadExpiresAt())) {
                    LOG.debug(
                            "服务端文件上传已过期 | fileId={} backendId={} uploadExpiresAt={}",
                            record.id(),
                            record.backendId(),
                            record.uploadExpiresAt());
                    throw new BusinessException(FileErrorCode.UPLOAD_EXPIRED);
                }

                // 把服务端持有的流写入临时对象，完成核验前不把临时对象作为正式文件读取来源。
                try {
                    storages.storage(record.backendId())
                            .write(
                                    uploadObject(record),
                                    record.declaredContentType(),
                                    record.declaredSizeBytes(),
                                    checkedContent);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                } catch (IllegalArgumentException exception) {
                    throw new BusinessException(FileErrorCode.CONTENT_MISMATCH, exception);
                } catch (BusinessException exception) {
                    throw exception;
                } catch (RuntimeException exception) {
                    LOG.warn(
                            "文件存储无法接收服务端上传 | fileId={} backendId={} errorType={}",
                            record.id(),
                            record.backendId(),
                            exception.getClass().getSimpleName());
                    throw new BusinessException(FileErrorCode.BACKEND_UNAVAILABLE, exception);
                }
                LOG.debug(
                        "服务端文件内容已写入 | fileId={} principalId={} backendId={} contentType={} sizeBytes={}",
                        record.id(),
                        record.uploaderPrincipalId(),
                        record.backendId(),
                        record.declaredContentType(),
                        record.declaredSizeBytes());
                return completeUploadLocked(record, record.uploaderPrincipalId());
            });
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }

    /**
     * 保存客户端上传和服务端上传共同使用的 PENDING 文件记录。
     *
     * @param uploader 创建文件的 Principal
     * @param intent   App 已经确认用途和内容限制的上传意图
     * @return 包含固定 backend、临时对象键、正式对象键和上传期限的文件记录
     */
    private @NotNull FileRecord createPending(
            @NotNull Principal uploader,
            @NotNull FileUploadIntent intent) {
        // 校验 App 已确认的上传内容限制；目标 Resource 和 scene 已由 App 完成业务校验。
        long uploaderPrincipalId = Objects.requireNonNull(uploader, "上传 Principal 不能为空").principalId();
        if (uploaderPrincipalId <= 0) {
            LOG.trace("文件上传申请的 Principal 标识无效 | principalId={}", uploaderPrincipalId);
            throw new BusinessException(FileErrorCode.INVALID);
        }
        FileUploadIntent checked = Objects.requireNonNull(intent, "上传意图不能为空");
        SettingValues fileLimits = new SettingValues(
                List.of(settings.MAXIMUM_SIZE_BYTES.inspect(), settings.UPLOAD_TTL.inspect()));
        long maximumSizeBytes = fileLimits.get(settings.MAXIMUM_SIZE_BYTES);
        if (checked.sizeBytes() > maximumSizeBytes) {
            LOG.debug(
                    "文件上传申请超过大小限制 | principalId={} scene={} targetType={} targetId={} sizeBytes={} maxSizeBytes={}",
                    uploaderPrincipalId,
                    checked.use().scene(),
                    checked.use().target().type(),
                    checked.use().target().id(),
                    checked.sizeBytes(),
                    maximumSizeBytes);
            throw new BusinessException(FileErrorCode.INVALID);
        }

        // 选择并保存这条文件记录长期使用的存储实现，切换默认实现不会迁移历史文件。
        FileStorage storage = storages.storage(properties.getDefaultBackend());
        LOG.trace(
                "文件上传存储实现已选择 | backendId={} scene={} targetType={} targetId={} sizeBytes={}",
                storage.backendId(),
                checked.use().scene(),
                checked.use().target().type(),
                checked.use().target().id(),
                checked.sizeBytes());

        // 保存 PENDING 记录以及不可猜测的临时、正式对象键。
        long fileId = ids.nextId().value();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(fileLimits.get(settings.UPLOAD_TTL));
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String objectKey = Key.of(TreeCode.of("STORAGE:OBJECTS"))
                .part(OBJECT_MONTH.format(now))
                .part(fileId)
                .part(UUID.randomUUID().toString())
                .format(SLASH);
        FileRecord record = new FileRecord(
                fileId,
                uploaderPrincipalId,
                checked.use().scene(),
                checked.use().target().type(),
                checked.use().target().id(),
                storage.backendId(),
                storage.defaultContainer(),
                Key.of(TreeCode.of("STORAGE:UPLOADS")).part(fileId).part(nonce).format(SLASH),
                objectKey,
                checked.filename(),
                checked.contentType(),
                checked.sizeBytes(),
                null,
                null,
                FileStatus.PENDING,
                expiresAt,
                null,
                false);
        transactions.executeWithoutResult(status -> repository.createPending(record));
        LOG.trace(
                "待上传文件记录已创建 | fileId={} principalId={} backendId={} expiresAt={}",
                fileId,
                uploaderPrincipalId,
                record.backendId(),
                expiresAt);
        return record;
    }

    /**
     * 在同一文件锁内核验临时对象并完成转正，但对象存储调用不占用数据库事务。
     *
     * <p>正式对象已经存在而数据库仍为 {@link FileStatus#PENDING} 时，本方法继续保存完成状态，用于恢复对象转正后
     * 进程中断的情况。
     */
    @Override
    public @NotNull StoredFile completeUpload(long fileId, @NotNull Principal uploader) {
        if (fileId <= 0) {
            throw new BusinessException(FileErrorCode.INVALID);
        }
        long uploaderPrincipalId = Objects.requireNonNull(uploader, "上传 Principal 不能为空").principalId();
        if (uploaderPrincipalId <= 0) {
            LOG.trace("文件完成确认的 Principal 标识无效 | principalId={}", uploaderPrincipalId);
            throw new BusinessException(FileErrorCode.INVALID);
        }
        LOG.trace(
                "文件上传完成确认等待文件锁 | fileId={} principalId={}",
                fileId,
                uploaderPrincipalId);
        return distributedLock.execute(fileOperationLock(fileId), FILE_OPERATION_LOCK_WAIT, () -> {
            // 在短事务中读取状态；等待锁和之后的对象存储操作都不占用数据库事务。
            FileRecord record = Objects.requireNonNull(
                    transactions.execute(status -> find(fileId)),
                    "文件读取事务没有返回结果");
            return completeUploadLocked(record, uploaderPrincipalId);
        });
    }

    /**
     * 在已经取得文件锁后核验上传对象、执行幂等转正并把 PENDING 记录更新为 READY。
     *
     * @param record              当前文件记录
     * @param uploaderPrincipalId 请求完成上传的 Principal 标识
     * @return 已完成上传或先前已经完成的文件信息
     */
    private @NotNull StoredFile completeUploadLocked(
            @NotNull FileRecord record,
            long uploaderPrincipalId) {
        long fileId = record.id();
        LOG.trace(
                "文件上传完成确认已读取状态 | fileId={} principalId={} backendId={} status={}",
                fileId,
                uploaderPrincipalId,
                record.backendId(),
                record.status());

        // 只允许创建文件的 Principal 完成上传；对其他 Principal 隐藏文件是否存在这一事实。
        if (record.uploaderPrincipalId() != uploaderPrincipalId) {
            LOG.trace(
                    "文件上传完成确认 Principal 不匹配 | fileId={} principalId={} status={}",
                    fileId,
                    uploaderPrincipalId,
                    record.status());
            throw new BusinessException(FileErrorCode.NOT_FOUND);
        }
        if (record.status() == FileStatus.READY || record.status() == FileStatus.IN_USE) {
            LOG.trace("文件上传完成确认复用现有结果 | fileId={} status={}", fileId, record.status());
            return view(record);
        }
        if (record.status() != FileStatus.PENDING) {
            LOG.trace("文件上传完成确认状态冲突 | fileId={} status={}", fileId, record.status());
            throw new BusinessException(FileErrorCode.STATE_CONFLICT);
        }
        if (!clock.instant().isBefore(record.uploadExpiresAt())) {
            LOG.debug(
                    "文件上传完成确认已过期 | fileId={} backendId={} uploadExpiresAt={}",
                    fileId,
                    record.backendId(),
                    record.uploadExpiresAt());
            throw new BusinessException(FileErrorCode.UPLOAD_EXPIRED);
        }

        // 核验临时对象；正式对象已经存在时按上次转正成功、数据库未保存的中断恢复。
        FileStorage storage = storages.storage(record.backendId());
        StorageObject uploadObject = uploadObject(record);
        StorageObject storedObject = object(record);
        StorageObjectMetadata metadata;
        boolean alreadyPromoted;
        try {
            Optional<StorageObjectMetadata> uploaded = storage.inspect(uploadObject);
            alreadyPromoted = uploaded.isEmpty();
            metadata = uploaded.orElseGet(
                    () -> storage.inspect(storedObject)
                            .orElseThrow(() -> new BusinessException(FileErrorCode.INVALID)));
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LOG.warn(
                    "文件存储无法核验上传对象 | fileId={} backendId={} errorType={}",
                    fileId,
                    record.backendId(),
                    exception.getClass().getSimpleName());
            throw new BusinessException(FileErrorCode.BACKEND_UNAVAILABLE, exception);
        }
        LOG.trace(
                "文件上传对象已核验 | fileId={} backendId={} alreadyPromoted={} contentType={} sizeBytes={}",
                fileId,
                record.backendId(),
                alreadyPromoted,
                metadata.contentType(),
                metadata.sizeBytes());
        if (metadata.sizeBytes() != record.declaredSizeBytes()
                || !record.declaredContentType().equalsIgnoreCase(metadata.contentType())) {
            LOG.debug(
                    "文件上传内容与申请不一致 | fileId={} backendId={} declaredContentType={} actualContentType={} declaredSizeBytes={} actualSizeBytes={}",
                    fileId,
                    record.backendId(),
                    record.declaredContentType(),
                    metadata.contentType(),
                    record.declaredSizeBytes(),
                    metadata.sizeBytes());
            throw new BusinessException(FileErrorCode.CONTENT_MISMATCH);
        }

        // 存储实现负责幂等转正；完成后临时上传位置不再作为文件读取来源。
        try {
            storage.promote(uploadObject, storedObject);
        } catch (RuntimeException exception) {
            LOG.warn(
                    "文件存储无法转正上传对象 | fileId={} backendId={} errorType={}",
                    fileId,
                    record.backendId(),
                    exception.getClass().getSimpleName());
            throw new BusinessException(FileErrorCode.BACKEND_UNAVAILABLE, exception);
        }

        // 转正成功后在新事务中把数据库状态从 PENDING 改为 READY，并设置未绑定文件的保留期限。
        Instant readyExpiresAt = clock.instant().plus(settings.READY_RETENTION.get());
        FileRecord completed = record.completed(
                metadata.contentType(),
                metadata.sizeBytes(),
                storedObject.objectKey(),
                readyExpiresAt);
        boolean saved = TRUE.equals(
                transactions.execute(status -> repository.saveCompleted(completed)));
        if (!saved) {
            LOG.trace("文件上传完成状态保存冲突 | fileId={} expectedStatus={}", fileId, FileStatus.PENDING);
            throw new BusinessException(FileErrorCode.STATE_CONFLICT);
        }
        LOG.info(
                "文件上传已完成 | fileId={} principalId={} backendId={} sizeBytes={} readyExpiresAt={}",
                completed.id(),
                uploaderPrincipalId,
                completed.backendId(),
                metadata.sizeBytes(),
                readyExpiresAt);
        return view(completed);
    }

    @Override
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull StoredFile loadById(long fileId) {
        // 只返回文件状态、用途和内容信息，不暴露存储实现和对象位置。
        if (fileId <= 0) {
            throw new BusinessException(FileErrorCode.INVALID);
        }
        FileRecord record = find(fileId);
        LOG.trace(
                "文件记录已读取 | fileId={} status={} scene={} targetType={} targetId={}",
                fileId,
                record.status(),
                record.scene(),
                record.targetResourceType(),
                record.targetResourceId());
        return view(record);
    }

    @Override
    @Transactional
    public @NotNull StoredFile bind(long fileId, @NotNull FileUse use) {
        // 在同一事务中核对申请用途和 READY 保留期限，再原子切换为 IN_USE。
        if (fileId <= 0) {
            throw new BusinessException(FileErrorCode.INVALID);
        }
        FileUse expected = Objects.requireNonNull(use, "文件用途不能为空");
        FileRecord record = find(fileId);
        validateUse(record, expected);

        // 已绑定文件直接返回；只允许仍在 READY 保留期内的文件首次绑定。
        if (record.status() == FileStatus.IN_USE) {
            LOG.trace("文件绑定复用现有结果 | fileId={} status={}", fileId, record.status());
            return view(record);
        }
        Instant readyExpiresAt = record.readyExpiresAt();
        if (record.status() == FileStatus.READY
                && (readyExpiresAt == null || !clock.instant().isBefore(readyExpiresAt))) {
            LOG.debug(
                    "文件绑定时 READY 保留期已结束 | fileId={} readyExpiresAt={}",
                    fileId,
                    readyExpiresAt);
            throw new BusinessException(FileErrorCode.STATE_CONFLICT);
        }
        if (record.status() != FileStatus.READY) {
            LOG.trace("文件绑定状态冲突 | fileId={} status={}", fileId, record.status());
            throw new BusinessException(FileErrorCode.STATE_CONFLICT);
        }

        // 原子切换为 IN_USE；并发请求已经完成同一绑定时返回当前结果。
        if (!repository.saveState(record, record.withStatus(FileStatus.IN_USE), null)) {
            FileRecord current = find(fileId);
            validateUse(current, expected);
            if (current.status() == FileStatus.IN_USE) {
                LOG.trace("文件绑定并发请求复用现有结果 | fileId={} status={}", fileId, current.status());
                return view(current);
            }
            LOG.trace("文件绑定状态更新冲突 | fileId={} status={}", fileId, current.status());
            throw new BusinessException(FileErrorCode.STATE_CONFLICT);
        }
        FileRecord bound = record.withStatus(FileStatus.IN_USE);
        LOG.debug(
                "文件已绑定业务对象 | fileId={} scene={} targetType={} targetId={}",
                bound.id(),
                bound.scene(),
                bound.targetResourceType(),
                bound.targetResourceId());
        return view(bound);
    }

    @Override
    public void discard(long fileId, @NotNull FileUse use) {
        // 与上传和完成操作串行核对状态，只在业务事务中登记异步删除。
        if (fileId <= 0) {
            throw new BusinessException(FileErrorCode.INVALID);
        }
        FileUse expected = Objects.requireNonNull(use, "文件用途不能为空");
        distributedLock.execute(
                fileOperationLock(fileId),
                FILE_OPERATION_LOCK_WAIT,
                () -> {
                    transactions.executeWithoutResult(status -> {
                        FileRecord record = find(fileId);
                        validateUse(record, expected);
                        if (isDeletionRequested(record.status())) {
                            return;
                        }
                        if (record.status() != FileStatus.PENDING && record.status() != FileStatus.READY) {
                            throw new BusinessException(FileErrorCode.STATE_CONFLICT);
                        }
                        boolean discarded = repository
                                .saveState(record, record.withStatus(FileStatus.DELETE_PENDING), clock.instant());
                        if (!discarded) {
                            FileRecord current = find(record.id());
                            if (!isDeletionRequested(current.status())) {
                                throw new BusinessException(FileErrorCode.STATE_CONFLICT);
                            }
                        }
                        LOG.debug("未绑定文件已登记丢弃 | fileId={} status={}", record.id(), record.status());
                    });
                    return null;
                });
    }

    @Override
    @Transactional
    public void release(long fileId, @NotNull FileUse use) {
        // 核对绑定用途后只登记删除请求，实际对象删除由清理任务在事务外完成。
        if (fileId <= 0) {
            throw new BusinessException(FileErrorCode.INVALID);
        }
        FileUse expected = Objects.requireNonNull(use, "文件用途不能为空");
        FileRecord record = find(fileId);
        validateUse(record, expected);

        // 已经进入删除流程时保持幂等；尚未绑定的文件不能由业务释放。
        if (record.status() == FileStatus.DELETE_PENDING
                || record.status() == FileStatus.DELETING
                || record.status() == FileStatus.DELETE_FAILED
                || record.status() == FileStatus.DELETED) {
            LOG.trace("文件释放复用现有结果 | fileId={} status={}", fileId, record.status());
            return;
        }
        if (record.status() != FileStatus.IN_USE) {
            LOG.trace("文件释放状态冲突 | fileId={} status={}", fileId, record.status());
            throw new BusinessException(FileErrorCode.STATE_CONFLICT);
        }

        // 原子登记删除请求；并发请求已经登记时保持幂等。
        if (!repository.saveState(record, record.withStatus(FileStatus.DELETE_PENDING), clock.instant())) {
            FileRecord current = find(fileId);
            validateUse(current, expected);
            if (isDeletionRequested(current.status())) {
                LOG.trace("文件释放并发请求复用现有结果 | fileId={} status={}", fileId, current.status());
                return;
            }
            LOG.trace("文件释放状态更新冲突 | fileId={} status={}", fileId, current.status());
            throw new BusinessException(FileErrorCode.STATE_CONFLICT);
        }
        LOG.debug(
                "文件已释放并等待删除 | fileId={} scene={} targetType={} targetId={}",
                record.id(),
                record.scene(),
                record.targetResourceType(),
                record.targetResourceId());
    }

    @Override
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull FileAccessUrl> createAccessUrls(
            @NotNull Collection<@NotNull Long> fileIds) {
        // 校验并去重文件标识；一批请求最多生成一百个地址。
        Collection<@NotNull Long> checked = Objects.requireNonNull(fileIds, "文件标识不能为空");
        if (checked.isEmpty() || checked.size() > 100) {
            LOG.trace("文件读取地址数量无效 | requestedCount={}", checked.size());
            throw new BusinessException(FileErrorCode.INVALID);
        }
        if (checked.stream().anyMatch(id -> id <= 0)) {
            LOG.trace("文件读取地址包含无效标识 | requestedCount={}", checked.size());
            throw new BusinessException(FileErrorCode.INVALID);
        }

        // 一次读取全部记录并完整核对，任一文件缺失时不返回部分结果。
        List<Long> ids = checked.stream().distinct().toList();
        List<FileRecord> found = Objects.requireNonNull(
                transactions.execute(status -> repository.findAll(ids)),
                "文件批量读取事务没有返回结果");
        Map<Long, FileRecord> records = new LinkedHashMap<>();
        for (FileRecord record : found)
            records.put(record.id(), record);
        if (records.size() != ids.size()) {
            LOG.trace(
                    "文件读取地址存在缺失记录 | requestedCount={} distinctCount={} foundCount={}",
                    checked.size(),
                    ids.size(),
                    records.size());
            throw new BusinessException(FileErrorCode.NOT_FOUND);
        }

        // 按去重后的输入顺序核对状态并签发地址，READY 文件的地址不能超过其保留期限。
        Instant now = clock.instant();
        Instant expiresAt = now.plus(settings.ACCESS_TTL.get());
        List<FileAccessUrl> result = new ArrayList<>(ids.size());
        for (Long id : ids) {
            FileRecord record = records.get(id);
            if (record.status() != FileStatus.READY && record.status() != FileStatus.IN_USE) {
                LOG.trace("文件状态不允许生成读取地址 | fileId={} status={}", id, record.status());
                throw new BusinessException(FileErrorCode.STATE_CONFLICT);
            }
            Instant accessExpiresAt = expiresAt;
            if (record.status() == FileStatus.READY) {
                Instant readyExpiresAt = record.readyExpiresAt();
                if (readyExpiresAt == null || !now.isBefore(readyExpiresAt)) {
                    LOG.debug(
                            "READY 文件保留期已结束 | fileId={} readyExpiresAt={}",
                            id,
                            readyExpiresAt);
                    throw new BusinessException(FileErrorCode.STATE_CONFLICT);
                }
                if (readyExpiresAt.isBefore(accessExpiresAt)) {
                    accessExpiresAt = readyExpiresAt;
                }
            }
            URI url;
            try {
                // 每条记录始终使用创建时保存的 backendId，切换默认存储不会改变历史文件位置。
                url = storages.storage(record.backendId()).prepareAccess(id, object(record), accessExpiresAt);
            } catch (BusinessException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                LOG.warn(
                        "文件存储无法签发读取地址 | fileId={} backendId={} errorType={}",
                        id,
                        record.backendId(),
                        exception.getClass().getSimpleName());
                throw new BusinessException(FileErrorCode.BACKEND_UNAVAILABLE, exception);
            }
            LOG.trace(
                    "文件读取地址已生成 | fileId={} backendId={} status={} expiresAt={}",
                    id,
                    record.backendId(),
                    record.status(),
                    accessExpiresAt);
            result.add(new FileAccessUrl(id, url, accessExpiresAt));
        }
        LOG.debug("文件读取地址批量生成完成 | requestedCount={} resultCount={}", checked.size(), result.size());
        return List.copyOf(result);
    }

    @Override
    public void receiveUpload(
            @NotNull String token,
            @NotNull String contentType,
            long contentLength,
            @NotNull InputStream content)
            throws IOException {
        // 验证短期 PUT Token，只记录验签得到的 fileId；Token 本身不得进入日志。
        long fileId = transferTokens.verify("PUT", token);
        LOG.trace("文件上传内容等待文件锁 | fileId={} contentLength={}", fileId, contentLength);
        try {
            distributedLock.execute(fileOperationLock(fileId), FILE_OPERATION_LOCK_WAIT, () -> {
                // 在写入对象前核对 PENDING 状态、有效期、媒体类型和声明字节数。
                FileRecord record = Objects.requireNonNull(
                        transactions.execute(status -> find(fileId)),
                        "文件读取事务没有返回结果");
                if (!clock.instant().isBefore(record.uploadExpiresAt())) {
                    LOG.debug(
                            "文件上传内容接收已过期 | fileId={} backendId={} uploadExpiresAt={}",
                            fileId,
                            record.backendId(),
                            record.uploadExpiresAt());
                    throw new BusinessException(FileErrorCode.UPLOAD_EXPIRED);
                }
                if (record.status() != FileStatus.PENDING) {
                    LOG.trace("文件状态不允许接收上传内容 | fileId={} status={}", fileId, record.status());
                    throw new BusinessException(FileErrorCode.STATE_CONFLICT);
                }
                if (contentLength != record.declaredSizeBytes()
                        || !record.declaredContentType().equalsIgnoreCase(contentType)) {
                    LOG.debug(
                            "文件上传请求与申请不一致 | fileId={} declaredContentType={} requestContentType={} declaredSizeBytes={} requestSizeBytes={}",
                            fileId,
                            record.declaredContentType(),
                            contentType,
                            record.declaredSizeBytes(),
                            contentLength);
                    throw new BusinessException(FileErrorCode.CONTENT_MISMATCH);
                }

                // 把请求流写入临时对象；存储实现负责核对实际读取字节数。
                try {
                    storages.storage(record.backendId())
                            .write(
                                    uploadObject(record),
                                    record.declaredContentType(),
                                    record.declaredSizeBytes(),
                                    content);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                } catch (IllegalArgumentException exception) {
                    throw new BusinessException(FileErrorCode.CONTENT_MISMATCH, exception);
                } catch (RuntimeException exception) {
                    LOG.warn(
                            "文件存储无法接收上传内容 | fileId={} backendId={} errorType={}",
                            fileId,
                            record.backendId(),
                            exception.getClass().getSimpleName());
                    throw new BusinessException(FileErrorCode.BACKEND_UNAVAILABLE, exception);
                }
                LOG.debug(
                        "文件上传内容已接收 | fileId={} backendId={} contentType={} sizeBytes={}",
                        fileId,
                        record.backendId(),
                        record.declaredContentType(),
                        record.declaredSizeBytes());
                return null;
            });
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }

    @Override
    @CheckReturnValue
    public @NotNull FileContent openAccess(@NotNull String token) throws IOException {
        // 验证短期 GET Token 后重新读取文件状态，不信任签发时的旧状态。
        long fileId = transferTokens.verify("GET", token);
        FileRecord record = Objects.requireNonNull(
                transactions.execute(status -> find(fileId)),
                "文件读取事务没有返回结果");
        return openRecordContent(record, fileId);
    }

    @Override
    @CheckReturnValue
    public @NotNull FileContent openForServer(long fileId) throws IOException {
        if (fileId <= 0) {
            throw new BusinessException(FileErrorCode.INVALID);
        }
        FileRecord record = Objects.requireNonNull(
                transactions.execute(status -> find(fileId)),
                "文件读取事务没有返回结果");
        return openRecordContent(record, fileId);
    }

    /**
     * 打开已经完成文件的正式对象；调用方已经完成用途和权限校验。
     */
    private @NotNull FileContent openRecordContent(
            @NotNull FileRecord record,
            long fileId) throws IOException {
        String contentType = record.contentType();
        Long sizeBytes = record.sizeBytes();
        if ((record.status() != FileStatus.READY && record.status() != FileStatus.IN_USE)
                || contentType == null
                || sizeBytes == null) {
            LOG.trace(
                    "文件状态不允许打开内容流 | fileId={} status={} hasContentType={} hasSizeBytes={}",
                    fileId,
                    record.status(),
                    contentType != null,
                    sizeBytes != null);
            throw new BusinessException(FileErrorCode.NOT_FOUND);
        }
        if (record.status() == FileStatus.READY
                && (record.readyExpiresAt() == null || !clock.instant().isBefore(record.readyExpiresAt()))) {
            LOG.trace(
                    "READY 文件保留期已结束，不能打开内容流 | fileId={} readyExpiresAt={}",
                    fileId,
                    record.readyExpiresAt());
            throw new BusinessException(FileErrorCode.NOT_FOUND);
        }
        try {
            // 只打开正式对象；返回的流由 HTTP 路由或服务端业务负责关闭。
            InputStream stream = storages.storage(record.backendId()).open(object(record));
            LOG.trace(
                    "文件内容流已打开 | fileId={} backendId={} status={} contentType={} sizeBytes={}",
                    fileId,
                    record.backendId(),
                    record.status(),
                    contentType,
                    sizeBytes);
            return new FileContent(stream, contentType, sizeBytes);
        } catch (BusinessException | IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LOG.warn(
                    "文件存储无法打开内容流 | fileId={} backendId={} errorType={}",
                    fileId,
                    record.backendId(),
                    exception.getClass().getSimpleName());
            throw new BusinessException(FileErrorCode.BACKEND_UNAVAILABLE, exception);
        }
    }

    /**
     * 读取必须存在的文件记录，不存在时返回稳定的文件错误。
     */
    private @NotNull FileRecord find(long id) {
        return repository.find(id).orElseThrow(() -> new BusinessException(FileErrorCode.NOT_FOUND));
    }

    /**
     * 核对调用方提交的 scene 和目标 Resource 与上传申请完全一致。
     */
    private static void validateUse(@NotNull FileRecord record, @NotNull FileUse use) {
        if (!record.scene().equals(use.scene())
                || !record.targetResourceType().equals(use.target().type())
                || !record.targetResourceId().equals(use.target().id())) {
            LOG.trace(
                    "文件用途与上传申请不一致 | fileId={} actualScene={} expectedScene={} actualTargetType={} expectedTargetType={} actualTargetId={} expectedTargetId={}",
                    record.id(),
                    record.scene(),
                    use.scene(),
                    record.targetResourceType(),
                    use.target().type(),
                    record.targetResourceId(),
                    use.target().id());
            throw new BusinessException(FileErrorCode.STATE_CONFLICT);
        }
    }

    /**
     * 返回文件是否已经进入释放后的删除流程。
     */
    private static boolean isDeletionRequested(@NotNull FileStatus status) {
        return status == FileStatus.DELETE_PENDING
                || status == FileStatus.DELETING
                || status == FileStatus.DELETE_FAILED
                || status == FileStatus.DELETED;
    }

    /**
     * 返回上传写入和完成确认共同使用的文件锁键。
     */
    private static @NotNull LockKey fileOperationLock(long fileId) {
        return new LockKey(TreeCode.of("STORAGE:FILE"), fileId);
    }

    /**
     * 返回上传请求只能写入的临时对象位置。
     */
    private static @NotNull StorageObject uploadObject(@NotNull FileRecord record) {
        return new StorageObject(record.containerName(), record.uploadObjectKey());
    }

    /**
     * 返回完成上传后的正式对象位置；尚未分配时拒绝继续。
     */
    private static @NotNull StorageObject object(@NotNull FileRecord record) {
        String objectKey = record.objectKey();
        if (objectKey == null)
            throw new BusinessException(FileErrorCode.STATE_CONFLICT);
        return new StorageObject(record.containerName(), objectKey);
    }

    /**
     * 把内部文件记录转换为不暴露 backend、container 和对象键的 Core 结果。
     */
    private static @NotNull StoredFile view(@NotNull FileRecord record) {
        String actualContentType = record.contentType();
        Long actualSizeBytes = record.sizeBytes();
        String contentType = actualContentType == null ? record.declaredContentType() : actualContentType;
        long sizeBytes = actualSizeBytes == null ? record.declaredSizeBytes() : actualSizeBytes;
        return new StoredFile(
                record.id(),
                record.uploaderPrincipalId(),
                new FileUse(
                        record.scene(),
                        new Resource.Ref(record.targetResourceType(), record.targetResourceId())),
                record.filename(),
                contentType,
                sizeBytes,
                record.status());
    }

}
