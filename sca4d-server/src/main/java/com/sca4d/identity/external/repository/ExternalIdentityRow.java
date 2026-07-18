package com.sca4d.identity.external.repository;

import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.subject.SubjectStatus;
import com.sca4d.identity.IdentityProviderStatus;
import lombok.Data;

import java.time.OffsetDateTime;

/** ExternalIdentity 与 Provider、Account、Subject 当前状态的数据库查询结果。 */
@Data
public class ExternalIdentityRow {

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
    private AccountScopeType providerScopeType;
    private Long providerScopeId;
    private IdentityProviderStatus providerStatus;
    private Long subjectId;
    private String subjectDisplayName;
    private SubjectStatus subjectStatus;
    private Long subjectVersion;
    private AccountScopeType accountScopeType;
    private Long accountScopeId;
    private AccountStatus accountStatus;
    private Boolean secondFactorRequired;
    private Long accountVersion;
}
