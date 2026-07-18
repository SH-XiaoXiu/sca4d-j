package com.sca4d.iam.account;

import com.sca4d.common.auth.AccountScopeType;
import lombok.Data;

import java.time.OffsetDateTime;

/** 管理端列表使用的 Account 读取对象。 */
@Data
public class ManagedAccount {
    private Long accountId;
    private Long subjectId;
    private Long defaultPrincipalId;
    private String displayName;
    private String primaryIdentifier;
    private Long avatarFileId;
    private Long subjectVersion;
    private Long profileVersion;
    private AccountScopeType scopeType;
    private Long scopeId;
    private AccountStatus status;
    private boolean secondFactorRequired;
    private Long version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
