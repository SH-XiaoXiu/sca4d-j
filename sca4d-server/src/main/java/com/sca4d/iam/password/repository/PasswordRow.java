package com.sca4d.iam.password.repository;

import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.credential.CredentialStatus;
import com.sca4d.iam.subject.SubjectStatus;
import lombok.Data;
import lombok.experimental.Accessors;

/** PasswordCredentialScheme 验证一次指定 Account 所需的数据库行。 */
@Data
@Accessors(chain = true)
public class PasswordRow {
    private Long credentialId;
    private Long accountId;
    private Long subjectId;
    private AccountScopeType scopeType;
    private Long scopeId;
    private String displayName;
    private String passwordHash;
    private CredentialStatus credentialStatus;
    private AccountStatus accountStatus;
    private Boolean secondFactorRequired;
    private SubjectStatus subjectStatus;
    private Long credentialVersion;
}
