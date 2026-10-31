package com.sca4d.open.client;

import com.sca4d.core.api.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 表示 Open Client 是否允许继续认证和调用。 */
public enum OpenClientStatus implements BaseEnum {
    /** 允许认证和调用。 */
    ACTIVE,
    /** 停止新的认证和调用。 */
    DISABLED;

    @Override
    public @NotNull String value() {
        return name();
    }

    @Override
    public @NotNull String description() {
        return this == ACTIVE ? "有效" : "已停用";
    }
}
