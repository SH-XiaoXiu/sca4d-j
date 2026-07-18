package com.sca4d.im;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/**
 * IM 消息状态。
 */
public enum ImMessageStatus implements BaseEnum {

    /** 正常消息。 */
    NORMAL("正常消息"),
    /** 已撤回消息。 */
    REVOKED("已撤回消息");

    private final String value;
    private final String description;

    ImMessageStatus(@NotNull String description) {
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
