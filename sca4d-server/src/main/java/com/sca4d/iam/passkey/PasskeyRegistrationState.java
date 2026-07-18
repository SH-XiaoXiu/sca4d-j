package com.sca4d.iam.passkey;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** Redis 中 Passkey registration 是否仍可提交认证器响应。 */
public enum PasskeyRegistrationState implements BaseEnum {
    /** 已创建 WebAuthn challenge，等待浏览器提交 attestation。 */
    PENDING("PENDING", "等待认证器响应"),
    /** 已被一次 Credential 创建请求消费。 */
    CONSUMED("CONSUMED", "已消费");

    private final String value;
    private final String description;

    PasskeyRegistrationState(@NotNull String value, @NotNull String description) {
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
