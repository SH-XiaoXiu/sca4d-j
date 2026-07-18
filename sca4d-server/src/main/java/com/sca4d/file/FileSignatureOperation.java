package com.sca4d.file;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 文件签名操作。 */
public enum FileSignatureOperation implements BaseEnum {
    /** 申请上传票据。 */
    UPLOAD("申请上传票据"),
    /** 申请读取地址。 */
    READ("申请读取地址");

    private final String value;
    private final String description;

    FileSignatureOperation(@NotNull String description) {
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
