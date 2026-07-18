package com.sca4d.identity;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 外部身份源使用的标准认证协议。 */
public enum IdentityProviderProtocol implements BaseEnum {
    /** 使用 Discovery、ID Token 和 UserInfo 的 OpenID Connect。 */
    OIDC("OIDC", "OpenID Connect"),
    /** 使用标准端点和只读 JSONPath Profile 映射的 OAuth 2.0。 */
    OAUTH2("OAUTH2", "OAuth 2.0"),
    /** 使用服务账号搜索和用户 bind 的 LDAP。 */
    LDAP("LDAP", "LDAP");

    private final String value;
    private final String description;

    IdentityProviderProtocol(@NotNull String value, @NotNull String description) {
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
