package com.sca4d.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** {@code iam_password} 保存的 PASSWORD Credential Argon2id 摘要。 */
@Data
@Accessors(chain = true)
@Table("iam_password")
public class IamPasswordEntity {
    @Id
    private Long credentialId;
    private String passwordHash;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
