package com.sca4d.file;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 客户端文件上传方式。 */
public enum FileUploadMode implements BaseEnum {
    /** 客户端直接上传到对象存储。 */
    DIRECT("客户端直接上传到对象存储"),
    /** 客户端通过应用服务上传。 */
    PROXY("客户端通过应用服务上传");

    private final String value;
    private final String description;

    FileUploadMode(@NotNull String description) {
        this.value = BaseEnum.normalize(name());
        this.description = description;
    }

    @Override
    public @NotNull String value() {
        return value;
    }

    @Override
    public @NotNull String description() {
        return description;
    }
}
