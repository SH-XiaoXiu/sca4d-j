package com.sca4d.core.api.storage;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 描述存储实现实际读取到的对象媒体类型和大小。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class StorageObjectMetadata {
    private final @NotNull String contentType;

    private final long sizeBytes;

    /**
     * 创建存储对象元数据。
     *
     * @param contentType 存储实现读取到的媒体类型
     * @param sizeBytes 存储实现读取到的正数字节数
     * @throws IllegalArgumentException 字节数不是正数时抛出
     */
    public StorageObjectMetadata(@NotNull String contentType, long sizeBytes) {
        this.contentType = Objects.requireNonNull(contentType, "对象媒体类型不能为空");
        if (sizeBytes <= 0)
            throw new IllegalArgumentException("对象字节数必须为正数");
        this.sizeBytes = sizeBytes;
    }

    /**
     * 存储对象的媒体类型。
     *
     * @return 实际媒体类型
     */
    public @NotNull String contentType() {
        return contentType;
    }

    /**
     * 存储对象的字节数。
     *
     * @return 实际正数字节数
     */
    public long sizeBytes() {
        return sizeBytes;
    }
}
