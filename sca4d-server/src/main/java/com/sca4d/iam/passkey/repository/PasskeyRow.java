package com.sca4d.iam.passkey.repository;

import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.credential.CredentialStatus;
import com.sca4d.iam.subject.SubjectStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** WebAuthn 验证、账号安全展示和原子计数更新需要的 Passkey 持久化结果。 */
@Data
@Accessors(chain = true)
public class PasskeyRow {
    private Long credentialId;
    private Long accountId;
    private Long subjectId;
    private AccountScopeType scopeType;
    private Long scopeId;
    private String subjectDisplayName;
    private byte[] userHandle;
    private byte[] passkeyCredentialId;
    private byte[] publicKeyCose;
    private Long signatureCount;
    private byte[] aaguid;
    private Boolean backupEligible;
    private Boolean backupState;
    private String passkeyDisplayName;
    private OffsetDateTime lastUsedAt;
    private CredentialStatus credentialStatus;
    private AccountStatus accountStatus;
    private Boolean secondFactorRequired;
    private SubjectStatus subjectStatus;
    private Long credentialVersion;
}
