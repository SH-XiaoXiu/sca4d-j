package com.sca4d.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** {@code iam_passkey} 保存 WebAuthn 公钥、计数器和认证器备份状态。 */
@Data
@Accessors(chain = true)
@Table("iam_passkey")
public class IamPasskeyEntity {
    @Id
    private Long credentialId;

    private byte[] passkeyCredentialId;
    private byte[] publicKeyCose;
    private Long signatureCount;
    private byte[] aaguid;
    private Boolean backupEligible;
    private Boolean backupState;
    private String displayName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime lastUsedAt;
}
