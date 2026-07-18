package com.sca4d.identity.ldap;

import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.common.auth.AccountScopeType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 列出可用 LDAP Provider，并验证目录账号和 LDAP 密码。 */
public interface LdapAuthenticationService {

    /** @return 指定 Account 作用域中全部 ACTIVE LDAP Provider */
    @NotNull
    List<LdapAvailableProvider> providers(
            @NotNull AccountScopeType scopeType, @Nullable String tenantCode);

    /** @return 完成 LDAP bind 和外部身份处理后的认证 */
    @NotNull
    AuthenticationProcess authenticate(
            @NotNull String authenticationId,
            @NotNull String browserHash,
            @NotNull String providerCode,
            @NotNull String username,
            @NotNull String password);
}
