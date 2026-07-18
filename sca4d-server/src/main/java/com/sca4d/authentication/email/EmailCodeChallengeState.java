package com.sca4d.authentication.email;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** Redis 邮件验证码 Challenge 的可验证、已消费或耗尽状态。 */
public enum EmailCodeChallengeState implements BaseEnum {
    PENDING("PENDING", "等待验证"),
    CONSUMED("CONSUMED", "已消费"),
    EXHAUSTED("EXHAUSTED", "尝试次数已耗尽");

    private final String value;
    private final String description;

    EmailCodeChallengeState(@NotNull String value, @NotNull String description) {
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
