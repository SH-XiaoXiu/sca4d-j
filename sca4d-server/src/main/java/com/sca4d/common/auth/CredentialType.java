package com.sca4d.common.auth;

import com.sca4d.common.dictionary.CodeDictionary;
import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/**
 * 登录凭证类型。
 */
@CodeDictionary(code = "iam.credential-type", nameKey = "dictionary.iam.credential-type")
public enum CredentialType implements BaseEnum {
    /** 账号密码凭证。 */
    PASSWORD("PASSWORD", "账号密码"),
    /** 基于共享密钥和时间步的一次性验证码凭证。 */
    TOTP("TOTP", "身份验证器"),
    /** 使用 WebAuthn 公钥验证签名的 Passkey 凭证。 */
    PASSKEY("PASSKEY", "Passkey");

    private final String value;
    private final String description;

    CredentialType(@NotNull String value, @NotNull String description) {
        this.value = value;
        this.description = description;
    }

    @Override
    public @NotNull String value() {
        return value;
    }

    @Override
    public @NotNull String description() {
        return description;
    }
}
