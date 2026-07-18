package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import com.sca4d.common.auth.CredentialType;
import com.sca4d.iam.credential.CredentialStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** {@code iam_credential} 保存的认证材料公共事实。 */
@Data
@Accessors(chain = true)
@Table("iam_credential")
public class IamCredentialEntity {
    private Long id;
    private Long accountId;
    private CredentialType credentialType;
    private CredentialStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
    private Integer deleted;
}
