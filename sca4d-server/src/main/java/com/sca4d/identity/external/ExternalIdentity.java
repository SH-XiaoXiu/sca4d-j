package com.sca4d.identity.external;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;

/** IdentityProvider external subject 与一个本地 Account 的当前绑定。 */
@Getter
@AllArgsConstructor
public class ExternalIdentity {

    private final Long identityId;
    private final Long providerId;
    private final Long accountId;
    private final ExternalIdentityProfile profile;
    private final OffsetDateTime lastAuthenticatedAt;
    private final Long version;
}
