package com.sca4d.authentication.email.codec;

import org.jetbrains.annotations.NotNull;

/** Flowable 保存并交给邮件验证码方式复核的认证 UUID 与 Challenge UUID。 */
public final class EmailCodeChallengeReference {

    private static final String SEPARATOR = "/";

    private final String authenticationId;
    private final String challengeId;

    /**
     * 创建可以保存到 AuthMethodVerification.proofId 的邮件 Challenge 引用。
     *
     * @param authenticationId 认证 UUID
     * @param challengeId 邮件 Challenge UUID
     */
    public EmailCodeChallengeReference(@NotNull String authenticationId, @NotNull String challengeId) {
        if (authenticationId.contains(SEPARATOR) || challengeId.contains(SEPARATOR)) {
            throw new IllegalArgumentException("邮件验证码 Challenge 引用包含非法分隔符");
        }
        this.authenticationId = authenticationId;
        this.challengeId = challengeId;
    }

    /**
     * 从 Flowable 保存的 proofId 恢复 Challenge 引用。
     *
     * @param value {@link #value()} 生成的不透明标识
     * @return 认证 UUID 和 Challenge UUID
     */
    public static @NotNull EmailCodeChallengeReference parse(@NotNull String value) {
        int separator = value.indexOf(SEPARATOR);
        if (separator <= 0 || separator == value.length() - 1 || separator != value.lastIndexOf(SEPARATOR)) {
            throw new IllegalArgumentException("邮件验证码 Challenge 引用格式无效");
        }
        return new EmailCodeChallengeReference(
                value.substring(0, separator), value.substring(separator + 1));
    }

    /** @return 由认证 UUID 和 Challenge UUID 组成的短期引用 */
    public @NotNull String value() {
        return authenticationId + SEPARATOR + challengeId;
    }

    /** @return 认证 UUID */
    public @NotNull String getAuthenticationId() {
        return authenticationId;
    }

    /** @return 邮件 Challenge UUID */
    public @NotNull String getChallengeId() {
        return challengeId;
    }
}
