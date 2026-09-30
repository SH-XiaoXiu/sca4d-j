package com.sca4d.auth.email;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 返回匿名调用方可以安全读取的 Challenge 标识、脱敏提示和时间边界。 */
public class EmailCodeChallenge {

    private final @NotNull EmailCodeChallengeId challengeId;

    private final @NotNull Instant expiresAt;

    private final @NotNull Instant resendAt;

    private final @NotNull String maskedRecipient;

    /**
     * @param challengeId Challenge 标识
     * @param maskedRecipient 不包含完整邮箱的接收人提示
     * @param expiresAt 验证码到期时间
     * @param resendAt 最早重新签发时间
     */
    public EmailCodeChallenge(
            @NotNull EmailCodeChallengeId challengeId,
            @NotNull String maskedRecipient,
            @NotNull Instant expiresAt,
            @NotNull Instant resendAt) {
        this.challengeId = Objects.requireNonNull(challengeId, "Challenge 标识不能为空");
        this.maskedRecipient = Objects.requireNonNull(maskedRecipient, "接收人提示不能为空");
        this.expiresAt = Objects.requireNonNull(expiresAt, "Challenge 到期时间不能为空");
        this.resendAt = Objects.requireNonNull(resendAt, "Challenge 重发时间不能为空");
    }

    /** @return Challenge 标识 */
    @Contract(pure = true)
    public @NotNull EmailCodeChallengeId challengeId() {
        return challengeId;
    }

    /** @return 不包含完整邮箱的接收人提示 */
    @Contract(pure = true)
    public @NotNull String maskedRecipient() {
        return maskedRecipient;
    }

    /** @return 验证码到期时间 */
    @Contract(pure = true)
    public @NotNull Instant expiresAt() {
        return expiresAt;
    }

    /** @return 最早重新签发时间 */
    @Contract(pure = true)
    public @NotNull Instant resendAt() {
        return resendAt;
    }
}
