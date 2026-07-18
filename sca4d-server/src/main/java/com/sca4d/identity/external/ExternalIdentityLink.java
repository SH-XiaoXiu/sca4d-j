package com.sca4d.identity.external;

import com.sca4d.entity.IamAccountEntity;
import com.sca4d.entity.IamSubjectEntity;
import com.sca4d.identity.IdentityProviderScope;
import com.sca4d.identity.IdentityProviderStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** ExternalIdentity、Provider 作用域及其本地 Subject、Account 当前状态。 */
@Getter
@AllArgsConstructor
public class ExternalIdentityLink {

    private final ExternalIdentity identity;
    private final IdentityProviderScope providerScope;
    private final IdentityProviderStatus providerStatus;
    private final IamSubjectEntity subject;
    private final IamAccountEntity account;
}
