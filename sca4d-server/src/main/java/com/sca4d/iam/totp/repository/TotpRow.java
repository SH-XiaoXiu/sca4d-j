package com.sca4d.iam.totp.repository;

import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.credential.CredentialStatus;
import com.sca4d.iam.subject.SubjectStatus;
import lombok.Data;
import lombok.experimental.Accessors;

/** TOTP CredentialScheme 验证指定 Account 所需的加密密钥和认证状态。 */
@Data
@Accessors(chain = true)
public class TotpRow {
    private Long credentialId;
    private Long accountId;
    private Long subjectId;
    private AccountScopeType scopeType;
    private Long scopeId;
    private String displayName;
    private String secretKeyId;
    private byte[] secretNonce;
    private byte[] secretCiphertext;
    private Long lastAcceptedStep;
    private CredentialStatus credentialStatus;
    private AccountStatus accountStatus;
    private Boolean secondFactorRequired;
    private SubjectStatus subjectStatus;
    private Long credentialVersion;
}
