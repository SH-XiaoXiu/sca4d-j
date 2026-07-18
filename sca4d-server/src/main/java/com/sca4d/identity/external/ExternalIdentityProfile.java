package com.sca4d.identity.external;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 上游认证成功后得到的通用 external subject 和可选资料。 */
@Getter
@AllArgsConstructor
public class ExternalIdentityProfile {

    private final String externalSubject;
    private final String displayName;
    private final String email;
    private final Boolean emailVerified;
    private final String avatarUri;
}
