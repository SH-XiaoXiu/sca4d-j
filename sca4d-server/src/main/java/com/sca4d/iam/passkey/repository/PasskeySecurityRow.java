package com.sca4d.iam.passkey.repository;

import com.sca4d.iam.credential.CredentialStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** 一次批量查询返回的 Passkey 公共状态和账号安全展示字段。 */
@Data
@Accessors(chain = true)
public class PasskeySecurityRow {
    private Long credentialId;
    private Long accountId;
    private CredentialStatus credentialStatus;
    private OffsetDateTime credentialCreatedAt;
    private OffsetDateTime credentialUpdatedAt;
    private Long credentialVersion;
    private String passkeyDisplayName;
    private Boolean backupEligible;
    private Boolean backupState;
    private OffsetDateTime lastUsedAt;
}
