package com.sca4d.storage.file.persistence;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.storage.FileStatus;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * 保存一个文件的上传 Principal、业务用途、存储位置、内容信息和当前状态。
 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
@ApiStatus.Internal
public final class FileRecord {
    private final long id;

    private final long uploaderPrincipalId;

    private final @NotNull String scene;

    private final @NotNull TreeCode targetResourceType;

    private final @NotNull String targetResourceId;

    private final @NotNull String backendId;

    private final @NotNull String containerName;

    private final @NotNull String uploadObjectKey;

    private final @Nullable String objectKey;

    private final @NotNull String filename;

    private final @NotNull String declaredContentType;

    private final long declaredSizeBytes;

    private final @Nullable String contentType;

    private final @Nullable Long sizeBytes;

    private final @NotNull FileStatus status;

    private final @NotNull Instant uploadExpiresAt;

    private final @Nullable Instant readyExpiresAt;

    private final boolean uploadObjectCleaned;

    /**
     * 创建包含上传、存储和状态信息的文件记录。
     */
    public FileRecord(
            long id,
            long uploaderPrincipalId,
            @NotNull String scene,
            @NotNull TreeCode targetResourceType,
            @NotNull String targetResourceId,
            @NotNull String backendId,
            @NotNull String containerName,
            @NotNull String uploadObjectKey,
            @Nullable String objectKey,
            @NotNull String filename,
            @NotNull String declaredContentType,
            long declaredSizeBytes,
            @Nullable String contentType,
            @Nullable Long sizeBytes,
            @NotNull FileStatus status,
            @NotNull Instant uploadExpiresAt,
            @Nullable Instant readyExpiresAt,
            boolean uploadObjectCleaned) {
        this.id = id;
        this.uploaderPrincipalId = uploaderPrincipalId;
        this.scene = Objects.requireNonNull(scene, "文件场景不能为空");
        this.targetResourceType = Objects.requireNonNull(targetResourceType, "目标资源类型不能为空");
        this.targetResourceId = Objects.requireNonNull(targetResourceId, "目标资源标识不能为空");
        this.backendId = Objects.requireNonNull(backendId, "存储实现标识不能为空");
        this.containerName = Objects.requireNonNull(containerName, "存储容器不能为空");
        this.uploadObjectKey = Objects.requireNonNull(uploadObjectKey, "上传对象键不能为空");
        this.objectKey = objectKey;
        this.filename = Objects.requireNonNull(filename, "文件名不能为空");
        this.declaredContentType = Objects.requireNonNull(declaredContentType, "声明媒体类型不能为空");
        this.declaredSizeBytes = declaredSizeBytes;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.status = Objects.requireNonNull(status, "文件状态不能为空");
        this.uploadExpiresAt = Objects.requireNonNull(uploadExpiresAt, "上传到期时间不能为空");
        this.readyExpiresAt = readyExpiresAt;
        this.uploadObjectCleaned = uploadObjectCleaned;
    }

    /** @return 文件标识 */
    public long id() {
        return id;
    }

    /** @return 创建文件的 Principal 标识 */
    public long uploaderPrincipalId() {
        return uploaderPrincipalId;
    }

    /** @return 文件所属业务场景 */
    @NotNull
    public String scene() {
        return scene;
    }

    /** @return 文件关联的目标 Resource 类型 */
    @NotNull
    public TreeCode targetResourceType() {
        return targetResourceType;
    }

    /** @return 文件关联的目标 Resource 标识 */
    @NotNull
    public String targetResourceId() {
        return targetResourceId;
    }

    /** @return 保存文件内容的 Storage 标识 */
    @NotNull
    public String backendId() {
        return backendId;
    }

    /** @return Storage 中的容器名称 */
    @NotNull
    public String containerName() {
        return containerName;
    }

    /** @return 客户端或服务端上传内容时使用的临时对象键 */
    @NotNull
    public String uploadObjectKey() {
        return uploadObjectKey;
    }

    /** @return 上传完成后的正式对象键，尚未完成时为空 */
    @Nullable
    public String objectKey() {
        return objectKey;
    }

    /** @return 调用方提供的文件名 */
    @NotNull
    public String filename() {
        return filename;
    }

    /** @return 调用方声明的媒体类型 */
    @NotNull
    public String declaredContentType() {
        return declaredContentType;
    }

    /** @return 调用方声明的文件字节数 */
    public long declaredSizeBytes() {
        return declaredSizeBytes;
    }

    /** @return Storage 返回的实际媒体类型，尚未完成时为空 */
    @Nullable
    public String contentType() {
        return contentType;
    }

    /** @return Storage 返回的实际文件字节数，尚未完成时为空 */
    @Nullable
    public Long sizeBytes() {
        return sizeBytes;
    }

    /** @return 文件当前状态 */
    @NotNull
    public FileStatus status() {
        return status;
    }

    /** @return 临时上传对象的到期时间 */
    @NotNull
    public Instant uploadExpiresAt() {
        return uploadExpiresAt;
    }

    /** @return READY 文件的保留期限，不适用时为空 */
    @Nullable
    public Instant readyExpiresAt() {
        return readyExpiresAt;
    }

    /** @return 临时上传对象已经清理时为 {@code true} */
    public boolean uploadObjectCleaned() {
        return uploadObjectCleaned;
    }

    /** 返回包含正式对象信息、实际内容信息和 READY 保留期限的新记录。 */
    @NotNull
    public FileRecord completed(
            @NotNull String actualContentType,
            long actualSizeBytes,
            @NotNull String actualObjectKey,
            @NotNull Instant expiresAt) {
        return new FileRecord(
                id,
                uploaderPrincipalId,
                scene,
                targetResourceType,
                targetResourceId,
                backendId,
                containerName,
                uploadObjectKey,
                actualObjectKey,
                filename,
                declaredContentType,
                declaredSizeBytes,
                actualContentType,
                actualSizeBytes,
                FileStatus.READY,
                uploadExpiresAt,
                expiresAt,
                uploadObjectCleaned);
    }

    /** 返回只替换文件状态的新记录；绑定或请求删除时清除 READY 保留期限。 */
    @NotNull
    public FileRecord withStatus(@NotNull FileStatus targetStatus) {
        return new FileRecord(
                id,
                uploaderPrincipalId,
                scene,
                targetResourceType,
                targetResourceId,
                backendId,
                containerName,
                uploadObjectKey,
                objectKey,
                filename,
                declaredContentType,
                declaredSizeBytes,
                contentType,
                sizeBytes,
                targetStatus,
                uploadExpiresAt,
                targetStatus == FileStatus.IN_USE || targetStatus == FileStatus.DELETE_PENDING ? null : readyExpiresAt,
                uploadObjectCleaned);
    }
}
