package com.sca4d.iam.repository;

import com.sca4d.common.auth.CredentialType;
import com.sca4d.iam.credential.CredentialStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** Credential、Account 与 Subject JOIN 查询的数据库行。 */
@Data
@Accessors(chain = true)
public class CredentialRow {
    private Long credentialId;
    private Long accountId;
    private Long subjectId;
    private CredentialType credentialType;
    private String identifier;
    private CredentialStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
    private Integer deleted;
}
