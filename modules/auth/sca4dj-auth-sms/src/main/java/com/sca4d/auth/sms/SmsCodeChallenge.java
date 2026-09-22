package com.sca4d.auth.sms;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 返回调用方可以安全读取的 SMS Challenge 标识、脱敏提示和时间边界。 */
public final class SmsCodeChallenge {
    private final @NotNull SmsCodeChallengeId challengeId;

    private final @NotNull String maskedRecipient;

    private final @NotNull Instant expiresAt;

    private final @NotNull Instant resendAt;

    /** 创建安全 Challenge 响应。 */
    public SmsCodeChallenge(
            @NotNull SmsCodeChallengeId challengeId,
            @NotNull String maskedRecipient,
            @NotNull Instant expiresAt,
            @NotNull Instant resendAt) {
        this.challengeId = Objects.requireNonNull(challengeId, "Challenge 标识不能为空");
        this.maskedRecipient = Objects.requireNonNull(maskedRecipient, "接收人提示不能为空");
        this.expiresAt = Objects.requireNonNull(expiresAt, "到期时间不能为空");
        this.resendAt = Objects.requireNonNull(resendAt, "重发时间不能为空");
    }

    /** 执行 Contract 方法表示的业务操作。 */
    @Contract(pure = true)
    public @NotNull SmsCodeChallengeId challengeId() {
        return challengeId;
    }

    /** 执行 Contract 方法表示的业务操作。 */
    @Contract(pure = true)
    public @NotNull String maskedRecipient() {
        return maskedRecipient;
    }

    /** 执行 Contract 方法表示的业务操作。 */
    @Contract(pure = true)
    public @NotNull Instant expiresAt() {
        return expiresAt;
    }

    /** 执行 Contract 方法表示的业务操作。 */
    @Contract(pure = true)
    public @NotNull Instant resendAt() {
        return resendAt;
    }
}
