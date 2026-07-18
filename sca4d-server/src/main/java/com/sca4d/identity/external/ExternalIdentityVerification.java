package com.sca4d.identity.external;

import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.Subject;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** ExternalIdentity 认证后返回的本地 Subject、Account、Identity ID 与版本。 */
@Getter
@AllArgsConstructor
public class ExternalIdentityVerification {

    private final Subject subject;
    private final Account account;
    private final Long identityId;
    private final Long identityVersion;
}
