package com.sca4d.auth.email.challenge;

import com.sca4d.auth.email.EmailCodeChallenge;
import com.sca4d.auth.email.EmailCodeChallengeId;
import com.sca4d.core.api.concurrent.Version;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 保存 Redis 中的验证码摘要、可选 Account、状态、次数和时间边界。 */
public final class EmailCodeChallengeState {

    private final @NotNull EmailCodeChallengeId challengeId;

    private final long accountId;

    private final @NotNull String codeHash;

    private final @NotNull String maskedRecipient;

    private final @NotNull Instant expiresAt;

    private final @NotNull Instant resendAt;

    private final int attemptCount;

    private final @NotNull EmailCodeChallengeStatus status;

    private final @NotNull Version version;

    /**
     * @param challengeId Challenge 标识
     * @param accountId 真实目标 Account；零表示匿名 dummy
     * @param codeHash Argon2id 验证码摘要
     * @param maskedRecipient 安全接收人提示
     * @param expiresAt 到期时间
     * @param resendAt 最早重发时间
     * @param attemptCount 已失败次数
     * @param status 当前状态
     * @param version 原子修改版本
     */
    public EmailCodeChallengeState(
            @NotNull EmailCodeChallengeId challengeId,
            long accountId,
            @NotNull String codeHash,
            @NotNull String maskedRecipient,
            @NotNull Instant expiresAt,
            @NotNull Instant resendAt,
            int attemptCount,
            @NotNull EmailCodeChallengeStatus status,
            @NotNull Version version) {
        if (accountId < 0 || attemptCount < 0) {
            throw new IllegalArgumentException("Challenge Account 和失败次数不能为负数");
        }
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
    @Contract(pure = true)
    public @NotNull EmailCodeChallengeId challengeId() {
        return challengeId;
    }

    /** @return 真实 Account 标识；零表示 dummy */
    @Contract(pure = true)
    public long accountId() {
        return accountId;
    }

    /** @return Argon2id 验证码摘要 */
    @Contract(pure = true)
    public @NotNull String codeHash() {
        return codeHash;
    }

    /** @return 安全接收人提示 */
    @Contract(pure = true)
    public @NotNull String maskedRecipient() {
        return maskedRecipient;
    }

    /** @return 到期时间 */
    @Contract(pure = true)
    public @NotNull Instant expiresAt() {
        return expiresAt;
    }

    /** @return 最早重发时间 */
    @Contract(pure = true)
    public @NotNull Instant resendAt() {
        return resendAt;
    }

    /** @return 已失败次数 */
    @Contract(pure = true)
    public int attemptCount() {
        return attemptCount;
    }

    /** @return 当前状态 */
    @Contract(pure = true)
    public @NotNull EmailCodeChallengeStatus status() {
        return status;
    }

    /** @return 原子修改版本 */
    @Contract(pure = true)
    public @NotNull Version version() {
        return version;
    }

    /** @return 不包含验证码摘要和 Account 的安全响应 */
    @Contract(pure = true)
    public @NotNull EmailCodeChallenge publicChallenge() {
        return new EmailCodeChallenge(challengeId, maskedRecipient, expiresAt, resendAt);
    }
}
