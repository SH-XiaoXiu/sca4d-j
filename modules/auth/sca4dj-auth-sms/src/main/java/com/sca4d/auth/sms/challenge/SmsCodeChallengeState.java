package com.sca4d.auth.sms.challenge;

import com.sca4d.auth.sms.SmsCodeChallenge;
import com.sca4d.auth.sms.SmsCodeChallengeId;
import com.sca4d.core.api.concurrent.Version;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 保存 Redis 中的 SMS 验证码摘要、Account、状态、次数和时间边界。 */
public class SmsCodeChallengeState {
    private final @NotNull Instant expiresAt;

    private final @NotNull Instant resendAt;

    private final @NotNull SmsCodeChallengeId challengeId;

    private final @NotNull SmsCodeChallengeStatus status;

    private final @NotNull String codeHash;

    private final @NotNull String maskedRecipient;

    private final @NotNull Version version;

    private final int attemptCount;

    private final long accountId;

    /**
     * @param challengeId Challenge 标识
     * @param accountId 关联 Account 标识
     * @param codeHash 验证码摘要
     * @param maskedRecipient 脱敏后的接收目标
     * @param expiresAt Challenge 到期时间
     * @param resendAt 允许再次发送验证码的时间
     * @param attemptCount 已尝试次数
     * @param status 当前 Challenge 状态
     * @param version Redis 状态版本
     */
    public SmsCodeChallengeState(
            @NotNull SmsCodeChallengeId challengeId,
            long accountId,
            @NotNull String codeHash,
            @NotNull String maskedRecipient,
            @NotNull Instant expiresAt,
            @NotNull Instant resendAt,
            int attemptCount,
            @NotNull SmsCodeChallengeStatus status,
            @NotNull Version version) {
        if (accountId < 0 || attemptCount < 0)
            throw new IllegalArgumentException("Challenge 数值不能为负数");
        this.challengeId = Objects.requireNonNull(challengeId, "Challenge 标识不能为空");
        this.accountId = accountId;
        this.codeHash = Objects.requireNonNull(codeHash, "验证码摘要不能为空");
        this.maskedRecipient = Objects.requireNonNull(maskedRecipient, "接收人提示不能为空");
        this.expiresAt = Objects.requireNonNull(expiresAt, "到期时间不能为空");
        this.resendAt = Objects.requireNonNull(resendAt, "重发时间不能为空");
        this.attemptCount = attemptCount;
        this.status = Objects.requireNonNull(status, "Challenge 状态不能为空");
        this.version = Objects.requireNonNull(version, "Challenge 版本不能为空");
    }

    /** @return Challenge 标识 */
    public @NotNull SmsCodeChallengeId challengeId() {
        return challengeId;
    }

    /** @return 关联 Account 标识 */
    public long accountId() {
        return accountId;
    }

    /** @return 验证码摘要 */
    public @NotNull String codeHash() {
        return codeHash;
    }

    /** @return 脱敏后的接收目标 */
    public @NotNull String maskedRecipient() {
        return maskedRecipient;
    }

    /** @return Challenge 到期时间 */
    public @NotNull Instant expiresAt() {
        return expiresAt;
    }

    /** @return 允许再次发送验证码的时间 */
    public @NotNull Instant resendAt() {
        return resendAt;
    }

    /** @return 已尝试次数 */
    public int attemptCount() {
        return attemptCount;
    }

    /** @return 当前 Challenge 状态 */
    public @NotNull SmsCodeChallengeStatus status() {
        return status;
    }

    /** @return Redis 状态版本 */
    public @NotNull Version version() {
        return version;
    }

    /** @return 不包含验证码摘要和 Account 标识的公开 Challenge */
    public @NotNull SmsCodeChallenge publicChallenge() {
        return new SmsCodeChallenge(challengeId, maskedRecipient, expiresAt, resendAt);
    }
}
