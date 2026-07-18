package com.sca4d.common.auth;

import org.jetbrains.annotations.NotNull;

/** 只在 enrollment 激活调用栈中存在的 TOTP 密钥和首次已验证时间步。 */
public final class TotpCredential {

    private final String secret;
    private final long acceptedStep;

    /**
     * @param secret googleauth 生成的 Base32 密钥，只能交给 TOTP CredentialScheme 加密
     * @param acceptedStep 激活验证码已经验证的 30 秒 Unix 时间步
     */
    public TotpCredential(@NotNull String secret, long acceptedStep) {
        this.secret = secret;
        this.acceptedStep = acceptedStep;
    }

    /** @return 只供 TOTP CredentialScheme 在当前调用中加密的 Base32 密钥 */
    public @NotNull String secret() {
        return secret;
    }

    /** @return 激活时已经消费且后续不能再次接受的时间步 */
    public long acceptedStep() {
        return acceptedStep;
    }

    @Override
    public String toString() {
        return "TotpCredential[protected]";
    }
}
