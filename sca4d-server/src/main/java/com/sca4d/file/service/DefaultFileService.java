package com.sca4d.file.service;

import com.sca4d.common.core.TimeSupport;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.config.properties.Sca4djFileProperties;
import com.sca4d.entity.FileObjectEntity;
import com.sca4d.entity.FileResourceEntity;
import com.sca4d.file.FileAccessUrl;
import com.sca4d.file.FileResource;
import com.sca4d.file.FileService;
import com.sca4d.file.FileStatus;
import com.sca4d.file.FileUpload;
import com.sca4d.file.error.FileErrorCode;
import com.sca4d.file.repository.FileRepository;
import com.sca4d.file.signature.FileGrantSigner;
import com.sca4d.file.storage.FileContent;
import com.sca4d.file.storage.FileStorage;
import com.sca4d.file.storage.FileStorageMetadata;
import com.sca4d.file.storage.FileStorageProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 默认文件服务。 */
public class DefaultFileService implements FileService {

    private static final Logger log = LoggerFactory.getLogger(DefaultFileService.class);
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final DateTimeFormatter OBJECT_DATE = DateTimeFormatter.ofPattern("yyyy/MM");

    private final FileRepository repository;
    private final IdGenerator idGenerator;
    private final Sca4djFileProperties properties;
    private final FileGrantSigner grantSigner;
    private final Map<FileStorageProvider, FileStorage> storages;

    /**
     * 创建默认文件服务，并建立 storageProvider 到 FileStorage 的映射。
     *
     * @param repository 读写文件元数据和状态的仓储
     * @param idGenerator 文件和对象主键生成器
     * @param properties 文件大小、时效和默认存储配置
     * @param grantSigner 签发并校验上传占用和读取授权的签名器
     * @param storages 当前应用提供的全部文件存储实现
     * @throws IllegalStateException 同一存储提供方注册多个实现时抛出
     */
    public DefaultFileService(
            @NotNull FileRepository repository,
            @NotNull IdGenerator idGenerator,
            @NotNull Sca4djFileProperties properties,
            @NotNull FileGrantSigner grantSigner,
            @NotNull List<FileStorage> storages) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.properties = properties;
        this.grantSigner = grantSigner;
        this.storages = new LinkedHashMap<>();
        for (FileStorage storage : storages) {
            FileStorage existing = this.storages.putIfAbsent(storage.provider(), storage);
            if (existing != null) {
                throw new IllegalStateException("重复文件存储实现: " + storage.provider());
            }
        }
    }

    @Transactional
    @Override
    public @NotNull FileUpload createUpload(
            @NotNull Long principalId,
            @NotNull String originalFilename,
            @Nullable String declaredContentType,
            long sizeBytes) {
        if (sizeBytes <= 0) {
            throw new BizException(FileErrorCode.FILE_SIZE_POSITIVE);
        }
        if (sizeBytes > properties.getMaxFileSize().toBytes()) {
            throw new BizException(FileErrorCode.FILE_TOO_LARGE);
        }
        String filename = filename(originalFilename);
        String contentType = StringUtils.hasText(declaredContentType)
                ? declaredContentType.trim()
                : DEFAULT_CONTENT_TYPE;
        OffsetDateTime now = TimeSupport.now();
        OffsetDateTime expiresAt = now.plus(properties.getUploadTtl());
        Long fileId = idGenerator.nextId();
        String date = OBJECT_DATE.format(now);
        String temporaryObjectKey = "temporary/" + date + "/" + fileId;
        String objectKey = "objects/" + date + "/" + UUID.randomUUID();
        FileStorage storage = storage(properties.getStorage());

        FileResourceEntity resource = new FileResourceEntity()
                .setId(fileId)
                .setOwnerPrincipalId(principalId)
                .setOriginalFilename(filename)
                .setContentType(contentType)
                .setSizeBytes(sizeBytes)
                .setStatus(FileStatus.PENDING)
                .setUploadExpiresAt(expiresAt)
                .setCreatedAt(now)
                .setUpdatedAt(now)
                .setVersion(0L)
                .setDeleted(0);
        FileObjectEntity object = new FileObjectEntity()
                .setFileId(fileId)
                .setProvider(storage.provider())
                .setBucket(storage.bucket())
                .setObjectKey(objectKey)
                .setTemporaryObjectKey(temporaryObjectKey)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        repository.createUpload(resource, object);
        FileUpload upload = new FileUpload()
                .setFileId(fileId)
                .setInstruction(storage.prepareUpload(
                        fileId, temporaryObjectKey, contentType, properties.getUploadTtl(), expiresAt));
        log.debug(
                "文件上传已登记 | fileId={} status={} sizeBytes={} provider={}",
                fileId,
                FileStatus.PENDING,
                sizeBytes,
                storage.provider());
        return upload;
    }

    @Override
    public void writeUpload(
            @NotNull Long fileId,
            @NotNull Long principalId,
            @NotNull InputStream inputStream,
            long sizeBytes,
            @Nullable String contentType) {
        FileResource file = requireOwned(fileId, principalId);
        if (file.getStatus() != FileStatus.PENDING || file.getProvider() != FileStorageProvider.LOCAL) {
            throw new BizException(FileErrorCode.FILE_STATUS_INVALID);
        }
        if (file.getUploadExpiresAt() == null || !file.getUploadExpiresAt().isAfter(TimeSupport.now())) {
            throw new BizException(FileErrorCode.FILE_UPLOAD_EXPIRED);
        }
        if (sizeBytes != file.getSizeBytes()) {
            throw new BizException(FileErrorCode.FILE_UPLOAD_SIZE_MISMATCH);
        }
        storage(file.getProvider()).writeTemporary(
                file.getTemporaryObjectKey(),
                inputStream,
                sizeBytes,
                StringUtils.hasText(contentType) ? contentType.trim() : file.getContentType());
        log.info(
                "本地临时文件写入完成 | fileId={} sizeBytes={} provider={}",
                fileId,
                sizeBytes,
                file.getProvider());
    }

    @Transactional
    @Override
    public @NotNull FileResource completeUpload(@NotNull Long fileId, @NotNull Long principalId) {
        FileResource file = requireOwned(fileId, principalId);
        if (file.getStatus() == FileStatus.READY) {
            return file;
        }
        if (file.getStatus() != FileStatus.PENDING) {
            throw new BizException(FileErrorCode.FILE_STATUS_INVALID);
        }
        OffsetDateTime now = TimeSupport.now();
        if (file.getUploadExpiresAt() == null || !file.getUploadExpiresAt().isAfter(now)) {
            repository.updateStatus(fileId, Set.of(FileStatus.PENDING), FileStatus.DELETE_PENDING, now);
            throw new BizException(FileErrorCode.FILE_UPLOAD_EXPIRED);
        }
        FileStorage storage = storage(file.getProvider());
        FileStorageMetadata metadata;
        try {
            metadata = storage.stat(file.getTemporaryObjectKey());
        } catch (BizException exception) {
            if (exception.getErrorCode() != FileErrorCode.FILE_UPLOAD_INCOMPLETE) {
                throw exception;
            }
            // 对象提升成功而数据库提交失败后，最终对象是重试完成操作的可信来源。
            metadata = storage.stat(file.getObjectKey());
        }
        if (metadata.getSizeBytes() != file.getSizeBytes()
                || metadata.getSizeBytes() > properties.getMaxFileSize().toBytes()) {
            repository.updateStatus(fileId, Set.of(FileStatus.PENDING), FileStatus.DELETE_PENDING, now);
            throw new BizException(FileErrorCode.FILE_UPLOAD_SIZE_MISMATCH);
        }
        storage.promote(file.getTemporaryObjectKey(), file.getObjectKey());
        String completedContentType = file.getProvider() == FileStorageProvider.LOCAL
                ? file.getContentType()
                : (StringUtils.hasText(metadata.getContentType())
                        ? metadata.getContentType()
                        : file.getContentType());
        boolean completed = repository.completeUpload(
                fileId,
                principalId,
                completedContentType,
                metadata.getSizeBytes(),
                now);
        FileResource current = repository.find(fileId).orElseThrow(() -> new BizException(FileErrorCode.FILE_NOT_FOUND));
        if (!completed && current.getStatus() != FileStatus.READY) {
            throw new BizException(FileErrorCode.FILE_STATUS_INVALID);
        }
        log.info(
                "文件上传完成状态已写入事务 | fileId={} status={} sizeBytes={} provider={}",
                fileId,
                current.getStatus(),
                current.getSizeBytes(),
                current.getProvider());
        return current;
    }

    @Override
    public @NotNull FileResource requireReadyFile(@NotNull Long fileId, @NotNull Long principalId) {
        FileResource file = requireOwned(fileId, principalId);
        if (file.getStatus() != FileStatus.READY) {
            throw new BizException(FileErrorCode.FILE_STATUS_INVALID);
        }
        return file;
    }

    @Transactional
    @Override
    public @NotNull FileResource consumeFile(
            @NotNull Long fileId,
            @NotNull String uploadGrant,
            @NotNull String expectedScene,
            @Nullable String expectedResourceId,
            @NotNull Long principalId) {
        OffsetDateTime now = TimeSupport.now();
        grantSigner.requireUpload(
                uploadGrant, fileId, principalId, expectedScene, expectedResourceId, now);
        FileResource file = requireOwned(fileId, principalId);
        if (file.getStatus() == FileStatus.IN_USE) {
            throw new BizException(FileErrorCode.FILE_ALREADY_CONSUMED);
        }
        if (file.getStatus() != FileStatus.READY) {
            throw new BizException(FileErrorCode.FILE_STATUS_INVALID);
        }
        if (!repository.updateStatus(fileId, Set.of(FileStatus.READY), FileStatus.IN_USE, now)) {
            throw new BizException(FileErrorCode.FILE_ALREADY_CONSUMED);
        }
        FileResource consumed = repository.find(fileId)
                .orElseThrow(() -> new BizException(FileErrorCode.FILE_NOT_FOUND));
        log.info(
                "文件占用状态已写入事务 | fileId={} status={} provider={}",
                fileId,
                consumed.getStatus(),
                consumed.getProvider());
        return consumed;
    }

    @Override
    public @NotNull List<FileAccessUrl> createAccessUrls(@NotNull Collection<Long> fileIds) {
        List<Long> distinctIds = fileIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            return List.of();
        }
        Map<Long, FileResource> files = new LinkedHashMap<>();
        // 全部去重主键固定 1 条批量 SQL；后续不再按 fileId 回源数据库。
        for (FileResource file : repository.findAll(distinctIds)) {
            files.put(file.getFileId(), file);
        }
        OffsetDateTime expiresAt = TimeSupport.now().plus(properties.getAccessTtl());
        List<FileAccessUrl> results = new ArrayList<>();
        // 每个文件必须向所属存储提供方签发 1 个独立访问地址，因此外部调用数等于去重后的文件数。
        for (Long fileId : distinctIds) {
            FileResource file = files.get(fileId);
            if (file == null) {
                throw new BizException(FileErrorCode.FILE_NOT_FOUND);
            }
            if (file.getStatus() != FileStatus.IN_USE) {
                throw new BizException(FileErrorCode.FILE_STATUS_INVALID);
            }
            results.add(storage(file.getProvider()).createAccessUrl(
                    fileId,
                    file.getObjectKey(),
                    file.getOriginalFilename(),
                    properties.getAccessTtl(),
                    expiresAt));
        }
        return results;
    }

    @Transactional
    @Override
    public void requestDelete(@NotNull Long fileId) {
        repository.find(fileId).ifPresent(file -> {
            boolean changed = repository.updateStatus(
                    fileId,
                    Set.of(
                            FileStatus.PENDING,
                            FileStatus.READY,
                            FileStatus.IN_USE,
                            FileStatus.DELETE_FAILED,
                            FileStatus.EXPIRED),
                    FileStatus.DELETE_PENDING,
                    TimeSupport.now());
            if (changed) {
                log.info(
                        "文件删除请求已写入事务 | fileId={} status={} provider={}",
                        fileId,
                        FileStatus.DELETE_PENDING,
                        file.getProvider());
            } else {
                log.debug(
                        "文件删除请求未改变当前状态 | fileId={} status={} provider={}",
                        fileId,
                        file.getStatus(),
                        file.getProvider());
            }
        });
    }

    @Override
    public @NotNull FileContent openContent(@NotNull Long fileId, @NotNull String token) {
        OffsetDateTime now = TimeSupport.now();
        grantSigner.requireRead(token, fileId, now);
        FileResource file = repository.find(fileId).orElseThrow(() -> new BizException(FileErrorCode.FILE_NOT_FOUND));
        if (file.getStatus() != FileStatus.IN_USE || file.getProvider() != FileStorageProvider.LOCAL) {
            throw new BizException(FileErrorCode.FILE_STATUS_INVALID);
        }
        return storage(file.getProvider()).open(
                file.getObjectKey(), file.getOriginalFilename(), file.getContentType(), file.getSizeBytes());
    }

    @Override
    public void cleanup() {
        long startedAt = System.nanoTime();
        OffsetDateTime now = TimeSupport.now();
        int batchSize = properties.getCleanupBatchSize();
        int expiredCount = repository.expireUploads(now, now.minus(properties.getReadyTtl()), batchSize);
        OffsetDateTime leaseExpiredBefore = now.minus(properties.getCleanupLease());
        OffsetDateTime claimedAt = TimeSupport.now();
        List<FileResource> files = repository.claimDeletionBatch(leaseExpiredBefore, claimedAt, batchSize);
        if (expiredCount == 0 && files.isEmpty()) {
            log.debug("文件清理没有待处理文件 | batchSize={}", batchSize);
            return;
        }
        log.info(
                "文件清理批次开始 | expiredCount={} claimedCount={} batchSize={}",
                expiredCount,
                files.size(),
                batchSize);
        int deletedCount = 0;
        int retryCount = 0;
        int skippedCount = 0;
        // 每轮固定 1 条过期更新和 1 条批量抢占 SQL；最多处理 batchSize 个文件。对象存储删除必须逐文件隔离失败，
        // 每项随后执行 1 次带租约校验的结果写入。节点中断留下的 DELETING 会在租约到期后重新进入抢占范围。
        for (FileResource file : files) {
            Long fileId = file.getFileId();
            try {
                storage(file.getProvider()).delete(file.getTemporaryObjectKey(), file.getObjectKey());
                if (!repository.finishDeletion(fileId, claimedAt, FileStatus.DELETED, TimeSupport.now())) {
                    skippedCount++;
                    log.warn("文件对象已删除，但记录已由其他节点接管 | fileId={} provider={}", fileId, file.getProvider());
                } else {
                    deletedCount++;
                    log.debug("文件清理任务完成 | fileId={} provider={}", fileId, file.getProvider());
                }
            } catch (RuntimeException exception) {
                boolean failureRecorded = false;
                try {
                    failureRecorded = repository.finishDeletion(
                            fileId, claimedAt, FileStatus.DELETE_FAILED, TimeSupport.now());
                } catch (RuntimeException persistenceException) {
                    exception.addSuppressed(persistenceException);
                }
                retryCount++;
                log.warn(
                        "文件对象删除失败，等待下轮重试 | fileId={} failureRecorded={} provider={}",
                        fileId,
                        failureRecorded,
                        file.getProvider(),
                        exception);
            }
        }
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info(
                "文件清理批次完成 | expiredCount={} deletedCount={} retryCount={} skippedCount={} durationMs={}",
                expiredCount,
                deletedCount,
                retryCount,
                skippedCount,
                durationMs);
    }

    /**
     * 读取属于指定 Principal 的文件资源。
     *
     * @param fileId 目标文件主键
     * @param principalId 当前操作 Principal
     * @return 当前 Principal 所有的文件资源
     * @throws BizException 文件不存在或不属于当前 Principal 时抛出
     */
    private @NotNull FileResource requireOwned(@NotNull Long fileId, @NotNull Long principalId) {
        FileResource file = repository.find(fileId).orElseThrow(() -> new BizException(FileErrorCode.FILE_NOT_FOUND));
        if (!principalId.equals(file.getOwnerPrincipalId())) {
            throw new BizException(FileErrorCode.FILE_FORBIDDEN);
        }
        return file;
    }

    /**
     * 选择文件记录声明的存储实现。
     *
     * @param provider 文件对象持久化的存储提供方
     * @return 当前应用中唯一对应的存储实现
     * @throws IllegalStateException 数据引用了未启用的存储提供方时抛出
     */
    private @NotNull FileStorage storage(@NotNull FileStorageProvider provider) {
        FileStorage storage = storages.get(provider);
        if (storage == null) {
            throw new IllegalStateException("缺少文件存储实现: " + provider);
        }
        return storage;
    }

    /**
     * 将客户端文件名收敛为不包含目录部分的安全展示名称。
     *
     * <p>对象存储键由服务端独立生成；该名称只用于下载展示，仍拒绝空值、控制字符、非法平台路径和超长文本。
     *
     * @param value 客户端声明的原始文件名
     * @return 去除目录部分后的文件名
     * @throws BizException 文件名无法作为安全展示名称保存时抛出
     */
    private @NotNull String filename(@NotNull String value) {
        String normalized;
        try {
            Path path = Path.of(value.trim().replace('\\', '/'));
            Path filename = path.getFileName();
            normalized = filename == null ? "" : filename.toString();
        } catch (InvalidPathException exception) {
            throw new BizException(FileErrorCode.FILE_FILENAME_INVALID);
        }
        if (!StringUtils.hasText(normalized) || normalized.length() > 255 || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new BizException(FileErrorCode.FILE_FILENAME_INVALID);
        }
        return normalized;
    }
}
