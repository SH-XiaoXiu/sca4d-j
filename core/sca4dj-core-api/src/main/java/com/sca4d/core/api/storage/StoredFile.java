package com.sca4d.core.api.storage;

import com.sca4d.core.api.resource.Resource;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 返回文件标识、上传 Principal、业务用途、内容信息和当前状态。 */
public final class StoredFile implements Resource {
    /** 文件使用的稳定 Resource 类型。 */
    public static final @NotNull String RESOURCE_TYPE = "file";

    private final long id;

    private final long uploaderPrincipalId;

    private final @NotNull FileUse use;

    private final @NotNull String filename;

    private final @NotNull String contentType;

    private final long sizeBytes;

    private final @NotNull FileStatus status;

    /**
     * 创建供 App 检查上传 Principal、用途和状态的文件信息。
     *
     * @param id 正数文件标识
     * @param uploaderPrincipalId 创建文件的 Principal 标识
     * @param use 申请上传时声明的业务用途
     * @param filename 原始文件名
     * @param contentType 声明或实际媒体类型
     * @param sizeBytes 声明或实际正数字节数
     * @param status 当前持久化状态
     * @throws IllegalArgumentException 文件标识或字节数不是正数时抛出
     */
    public StoredFile(
            long id,
            long uploaderPrincipalId,
            @NotNull FileUse use,
            @NotNull String filename,
            @NotNull String contentType,
            long sizeBytes,
            @NotNull FileStatus status) {
        if (id <= 0) {
            throw new IllegalArgumentException("文件标识必须为正数");
        }
        if (uploaderPrincipalId <= 0) {
            throw new IllegalArgumentException("上传 Principal 标识必须为正数");
        }
        this.id = id;
        this.uploaderPrincipalId = uploaderPrincipalId;
        this.use = Objects.requireNonNull(use, "文件用途不能为空");
        this.filename = Objects.requireNonNull(filename, "文件名不能为空");
        this.contentType = Objects.requireNonNull(contentType, "文件媒体类型不能为空");
        if (sizeBytes <= 0)
            throw new IllegalArgumentException("文件字节数必须为正数");
        this.sizeBytes = sizeBytes;
        this.status = Objects.requireNonNull(status, "文件状态不能为空");
    }

    /**
     * 返回文件标识。
     *
     * @return 正数文件标识
     */
    public long fileId() {
        return id;
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull String type() {
        return RESOURCE_TYPE;
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull String id() {
        return Long.toString(id);
    }

    /**
     * 返回创建文件的 Principal 标识。
     *
     * @return 正数 Principal 标识
     */
    public long uploaderPrincipalId() {
        return uploaderPrincipalId;
    }

    /**
     * 返回申请上传时声明的业务用途。
     *
     * @return 文件用途
     */
    public @NotNull FileUse use() {
        return use;
    }

    /**
     * 返回调用方提供的原始文件名。
     *
     * @return 原始文件名
     */
    public @NotNull String filename() {
        return filename;
    }

    /**
     * 返回文件媒体类型。
     *
     * @return 上传完成前为声明值，上传完成后为存储对象实际值
     */
    public @NotNull String contentType() {
        return contentType;
    }

    /**
     * 返回文件字节数。
     *
     * @return 上传完成前为声明值，上传完成后为存储对象实际值
     */
    public long sizeBytes() {
        return sizeBytes;
    }

    /**
     * 返回文件当前状态。
     *
     * @return 持久化文件状态
     */
    public @NotNull FileStatus status() {
        return status;
    }
}
