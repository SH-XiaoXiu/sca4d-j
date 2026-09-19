package com.sca4d.openapi.client;

import com.sca4d.core.api.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 表示 OpenAPI Client 是否允许继续认证和调用。 */
public enum OpenApiClientStatus implements BaseEnum {
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
