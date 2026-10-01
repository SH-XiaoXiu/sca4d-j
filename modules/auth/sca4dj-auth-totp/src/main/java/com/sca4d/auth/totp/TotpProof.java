package com.sca4d.auth.totp;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 只在一次认证方法调用中存在的 TOTP 验证码。 */
public class TotpProof {

    private final String code;

    /**
     * 创建 TOTP 认证材料。
     *
     * @param code 身份验证器当前显示的六位验证码
     */
    public TotpProof(@NotNull String code) {
        this.code = Objects.requireNonNull(code, "TOTP 验证码不能为空");
    }

    @NotNull
    String code() {
        return code;
    }

    @Override
    public @NotNull String toString() {
        return "TotpProof[code=protected]";
    }
}
