package com.sca4d.auth.sms.challenge;

import com.sca4d.auth.sms.SmsCodeChallenge;
import com.sca4d.auth.sms.SmsCodeChallengeId;
import com.sca4d.core.api.concurrent.Version;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 保存 Redis 中的 SMS 验证码摘要、Account、状态、次数和时间边界。 */
public final class SmsCodeChallengeState {
    private final @NotNull SmsCodeChallengeId challengeId;

    private final long accountId;

    private final @NotNull String codeHash;

    private final @NotNull String maskedRecipient;

    private final @NotNull Instant expiresAt;

    private final @NotNull Instant resendAt;

    private final int attemptCount;

    private final @NotNull SmsCodeChallengeStatus status;

    private final @NotNull Version version;

    /** 创建 SMS Challenge Redis 状态。 */
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

    public @NotNull SmsCodeChallengeId challengeId() {
        return challengeId;
    }

    public long accountId() {
        return accountId;
    }

    public @NotNull String codeHash() {
        return codeHash;
    }

    public @NotNull String maskedRecipient() {
        return maskedRecipient;
    }

    public @NotNull Instant expiresAt() {
        return expiresAt;
    }

    public @NotNull Instant resendAt() {
        return resendAt;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public @NotNull SmsCodeChallengeStatus status() {
        return status;
    }

    public @NotNull Version version() {
        return version;
    }

    public @NotNull SmsCodeChallenge publicChallenge() {
        return new SmsCodeChallenge(challengeId, maskedRecipient, expiresAt, resendAt);
    }
}
