package com.sca4d.identity.external;

import com.sca4d.identity.IdentityProviderProtocol;
import com.sca4d.identity.IdentityProviderStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 当前 Account 可以展示和解除关联的 ExternalIdentity 及 Provider 摘要。 */
@Getter
@AllArgsConstructor
public class ExternalIdentityDetails {

    private final ExternalIdentity identity;
    private final String providerCode;
    private final String providerName;
    private final IdentityProviderProtocol providerProtocol;
    private final IdentityProviderStatus providerStatus;
}
