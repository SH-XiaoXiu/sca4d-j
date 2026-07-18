package com.sca4d.identity.external;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 外部协议认证完成后交给 AuthMethod 复核的 ExternalIdentity ID 与版本。 */
@Getter
@AllArgsConstructor
public class ExternalIdentityAuthenticationProof {

    private final Long identityId;
    private final Long identityVersion;
}
