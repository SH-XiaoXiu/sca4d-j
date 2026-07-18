package com.sca4d.file.storage;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 文件存储提供方。 */
public enum FileStorageProvider implements BaseEnum {
    /** 本地文件系统。 */
    LOCAL("本地文件系统"),
    /** MinIO 对象存储。 */
    MINIO("MinIO 对象存储");

    private final String description;

    FileStorageProvider(@NotNull String description) {
        this.description = description;
    }

    @Override
    public @NotNull String value() {
        return name();
    }

    @Override
    public @NotNull String description() {
        return description;
    }
}
