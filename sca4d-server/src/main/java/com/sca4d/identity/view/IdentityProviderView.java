package com.sca4d.identity.view;

import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.identity.IdentityProvider;
import com.sca4d.identity.IdentityProviderProtocol;
import com.sca4d.identity.IdentityProviderStatus;
import com.sca4d.identity.policy.IdentityProviderRegistrationPolicy;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/** 管理端可读取且不包含协议端点、Client Secret 或 LDAP 密码的 Provider。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdentityProviderView {

    private Long providerId;
    private AccountScopeType scopeType;
    private Long scopeId;
    private String code;
    private String name;
    private IdentityProviderProtocol protocol;
    private IdentityProviderRegistrationPolicy registrationPolicy;
    private Boolean emailAutoLink;
    private IdentityProviderStatus status;
    private Long version;

    /** @return Provider 通用字段的安全管理视图 */
    public static @NotNull IdentityProviderView from(@NotNull IdentityProvider provider) {
        return new IdentityProviderView(
                provider.getProviderId(),
                provider.getScope().getScopeType(),
                provider.getScope().getScopeId(),
                provider.getCode(),
                provider.getName(),
                provider.getProtocol(),
                provider.getRegistrationPolicy(),
                provider.getEmailAutoLink(),
                provider.getStatus(),
                provider.getVersion());
    }
}
