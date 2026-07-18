package com.sca4d.common.auth;

import org.jetbrains.annotations.NotNull;

/** 只在当前认证请求中交给 TOTP CredentialScheme 的六位验证码。 */
public final class TotpProof {

    private final String code;

    /** @param code 身份验证器当前显示的六位数字 */
    public TotpProof(@NotNull String code) {
        this.code = code;
    }

    /** @return 不得写入日志、审计、Session 或响应的六位验证码 */
    public @NotNull String code() {
        return code;
    }

    @Override
    public String toString() {
        return "TotpProof[protected]";
    }
}
