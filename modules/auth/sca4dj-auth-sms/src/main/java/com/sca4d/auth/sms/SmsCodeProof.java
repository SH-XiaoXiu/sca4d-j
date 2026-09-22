package com.sca4d.auth.sms;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存一次认证提交使用的 SMS Challenge 标识和原始六位验证码。 */
public final class SmsCodeProof {
    private final @NotNull SmsCodeChallengeId challengeId;

    private final @NotNull String code;

    /** 创建只在当前认证调用中存在的 SMS Code Proof。 */
    public SmsCodeProof(@NotNull SmsCodeChallengeId challengeId, @NotNull String code) {
        this.challengeId = Objects.requireNonNull(challengeId, "Challenge 标识不能为空");
        this.code = Objects.requireNonNull(code, "SMS 验证码不能为空");
    }

    /** 执行 challengeId 方法表示的业务操作。 */
    public @NotNull SmsCodeChallengeId challengeId() {
        return challengeId;
    }

    /** 返回 code 方法提供的结果。 */
    public @NotNull String code() {
        return code;
    }

    @Override
    public @NotNull String toString() {
        return "SmsCodeProof[challengeId=" + challengeId + ", code=protected]";
    }
}
