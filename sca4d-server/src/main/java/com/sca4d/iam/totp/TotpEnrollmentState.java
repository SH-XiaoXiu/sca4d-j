package com.sca4d.iam.totp;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** Redis 中 TOTP enrollment 当前是否仍能激活 Credential。 */
public enum TotpEnrollmentState implements BaseEnum {
    /** 已创建并等待二维码验证码。 */
    PENDING("PENDING", "等待激活"),
    /** 已被一次激活请求消费。 */
    CONSUMED("CONSUMED", "已消费");

    private final String value;
    private final String description;

    TotpEnrollmentState(@NotNull String value, @NotNull String description) {
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
