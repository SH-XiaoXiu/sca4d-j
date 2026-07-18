package com.sca4d.authentication.passkey;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** Redis 中 Passkey assertion 是否仍可创建 Proof。 */
public enum PasskeyAssertionState implements BaseEnum {
    /** 已创建 WebAuthn challenge，等待浏览器提交 assertion。 */
    PENDING("PENDING", "等待认证器响应"),
    /** 已被一次 Proof 请求消费。 */
    CONSUMED("CONSUMED", "已消费");

    private final String value;
    private final String description;

    PasskeyAssertionState(@NotNull String value, @NotNull String description) {
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
}
