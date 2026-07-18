package com.sca4d.identity.external.repository;

import com.sca4d.identity.IdentityProviderProtocol;
import com.sca4d.identity.IdentityProviderStatus;
import lombok.Data;

import java.time.OffsetDateTime;

/** 当前 Account 的 ExternalIdentity 及其 Provider 展示字段查询结果。 */
@Data
public class ExternalIdentityAccountRow {

    private Long identityId;
    private Long providerId;
    private Long accountId;
    private String externalSubject;
    private String externalDisplayName;
    private String email;
    private Boolean emailVerified;
    private String avatarUri;
    private OffsetDateTime lastAuthenticatedAt;
    private Long identityVersion;
    private String providerCode;
    private String providerName;
    private IdentityProviderProtocol providerProtocol;
    private IdentityProviderStatus providerStatus;
}
