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

    /** 执行 challengeId 方法表示的业务操作。 */
    public @NotNull SmsCodeChallengeId challengeId() {
        return challengeId;
    }

    /** 执行 accountId 方法表示的业务操作。 */
    public long accountId() {
        return accountId;
    }

    /** 返回 codeHash 方法提供的结果。 */
    public @NotNull String codeHash() {
        return codeHash;
    }

    /** 执行 maskedRecipient 方法表示的业务操作。 */
    public @NotNull String maskedRecipient() {
        return maskedRecipient;
    }

    /** 返回 expiresAt 方法提供的结果。 */
    public @NotNull Instant expiresAt() {
        return expiresAt;
    }

    /** 执行 resendAt 方法表示的业务操作。 */
    public @NotNull Instant resendAt() {
        return resendAt;
    }

    /** 返回 attemptCount 方法提供的结果。 */
    public int attemptCount() {
        return attemptCount;
    }

    /** 返回 status 方法提供的结果。 */
    public @NotNull SmsCodeChallengeStatus status() {
        return status;
    }

    /** 返回 version 方法提供的结果。 */
    public @NotNull Version version() {
        return version;
    }

    /** 执行 publicChallenge 方法表示的业务操作。 */
    public @NotNull SmsCodeChallenge publicChallenge() {
        return new SmsCodeChallenge(challengeId, maskedRecipient, expiresAt, resendAt);
    }
}
