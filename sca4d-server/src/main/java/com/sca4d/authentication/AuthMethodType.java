package com.sca4d.authentication;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 当前应用可以使用的认证方式。 */
public enum AuthMethodType implements BaseEnum {
    /** 作用域内账号标识与密码。 */
    PASSWORD("PASSWORD", "密码"),
    /** 通过邮件投递的一次性验证码。 */
    EMAIL_CODE("EMAIL_CODE", "邮件验证码"),
    /** Account 当前恢复码集合中的一次性恢复码。 */
    RECOVERY_CODE("RECOVERY_CODE", "恢复码"),
    /** Account 已激活身份验证器生成的时间型一次性验证码。 */
    TOTP("TOTP", "身份验证器验证码"),
    /** 使用 WebAuthn assertion 验证的 Passkey。 */
    PASSKEY("PASSKEY", "Passkey"),
    /** 使用外部 OpenID Provider 完成 Authorization Code + PKCE。 */
    OIDC("OIDC", "OpenID Connect"),
    /** 使用标准 OAuth2 Provider 完成 Authorization Code + PKCE。 */
    OAUTH2("OAUTH2", "OAuth 2.0"),
    /** 使用企业目录账号和密码完成 LDAP bind。 */
    LDAP("LDAP", "LDAP");

    private final String value;
    private final String description;

    AuthMethodType(@NotNull String value, @NotNull String description) {
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
