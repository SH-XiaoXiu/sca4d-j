package com.sca4d.authentication;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 认证方式允许用于哪些认证目的。 */
public enum AuthMethodCapability implements BaseEnum {
    LOGIN("LOGIN", "登录"),
    REGISTRATION("REGISTRATION", "注册"),
    LINKING("LINKING", "绑定"),
    STEP_UP("STEP_UP", "二次验证"),
    RECOVERY("RECOVERY", "恢复");

    private final String value;
    private final String description;

    AuthMethodCapability(@NotNull String value, @NotNull String description) {
        this.value = value;
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

    /** @return 与认证目的同名的认证方式用途 */
    public static @NotNull AuthMethodCapability from(@NotNull AuthenticationPurpose purpose) {
        return valueOf(purpose.name());
    }
}
