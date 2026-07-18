package com.sca4d.file;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 客户端执行文件上传时使用的 HTTP 方法。 */
public enum FileUploadMethod implements BaseEnum {
    /** 使用 PUT 请求把完整文件写入目标地址。 */
    PUT("PUT 上传");

    private final String value;
    private final String description;

    FileUploadMethod(@NotNull String description) {
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
