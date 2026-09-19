package com.sca4d.auth.email;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 只在一次认证方法调用中存在的 Email Code Challenge 标识和原始验证码。 */
public final class EmailCodeProof {

    private final @NotNull EmailCodeChallengeId challengeId;

    private final @NotNull String code;

    /** @param challengeId 待消费 Challenge @param code 调用方收到的六位验证码 */
    public EmailCodeProof(
            @NotNull EmailCodeChallengeId challengeId,
            @NotNull String code) {
        this.challengeId = Objects.requireNonNull(challengeId, "Challenge 标识不能为空");
        this.code = Objects.requireNonNull(code, "Email 验证码不能为空");
    }

    /** @return 待消费 Challenge */
    public @NotNull EmailCodeChallengeId challengeId() {
        return challengeId;
    }

    /** @return 调用方收到的原始验证码 */
    public @NotNull String code() {
        return code;
    }

    @Override
    public @NotNull String toString() {
        return "EmailCodeProof[challengeId=" + challengeId + ", code=protected]";
    }
}
