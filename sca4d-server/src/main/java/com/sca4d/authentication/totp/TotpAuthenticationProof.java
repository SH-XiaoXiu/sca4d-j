package com.sca4d.authentication.totp;

import org.jetbrains.annotations.NotNull;

/** 用户向当前 Authentication 提交的 TOTP 验证码。 */
public final class TotpAuthenticationProof {

    private final String code;

    /** @param code 身份验证器当前显示的六位数字 */
    public TotpAuthenticationProof(@NotNull String code) {
        this.code = code;
    }

    /** @return 只在当前方法调用中使用且不得记录的验证码 */
    public @NotNull String code() {
        return code;
    }

    @Override
    public String toString() {
        return "TotpAuthenticationProof[code=protected]";
    }
}
