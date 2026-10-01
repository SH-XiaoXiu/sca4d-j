package com.sca4d.auth.sms;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 返回调用方可以安全读取的 SMS Challenge 标识、脱敏提示和时间边界。 */
public class SmsCodeChallenge {
    private final @NotNull SmsCodeChallengeId challengeId;

    private final @NotNull String maskedRecipient;

    private final @NotNull Instant expiresAt;

    private final @NotNull Instant resendAt;

    /**
     * @param challengeId 短期 Challenge 标识
     * @param maskedRecipient 脱敏后的接收目标
     * @param expiresAt Challenge 到期时间
     * @param resendAt 允许再次发送验证码的时间
     */
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

    /** @return 短期 Challenge 标识 */
    @Contract(pure = true)
    public @NotNull SmsCodeChallengeId challengeId() {
        return challengeId;
    }

    /** @return 脱敏后的接收目标 */
    @Contract(pure = true)
    public @NotNull String maskedRecipient() {
        return maskedRecipient;
    }

    /** @return Challenge 到期时间 */
    @Contract(pure = true)
    public @NotNull Instant expiresAt() {
        return expiresAt;
    }

    /** @return 允许再次发送验证码的时间 */
    @Contract(pure = true)
    public @NotNull Instant resendAt() {
        return resendAt;
    }
}
