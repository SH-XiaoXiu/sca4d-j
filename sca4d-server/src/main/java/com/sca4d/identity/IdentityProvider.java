package com.sca4d.identity;

import com.sca4d.identity.policy.IdentityProviderRegistrationPolicy;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 不包含 Client Secret、LDAP 密码或协议端点的 IdentityProvider 管理资料。 */
@Getter
@AllArgsConstructor
public class IdentityProvider {

    private final Long providerId;
    private final IdentityProviderScope scope;
    private final String code;
    private final String name;
    private final IdentityProviderProtocol protocol;
    private final IdentityProviderRegistrationPolicy registrationPolicy;
    private final Boolean emailAutoLink;
    private final IdentityProviderStatus status;
    private final Long version;
}
