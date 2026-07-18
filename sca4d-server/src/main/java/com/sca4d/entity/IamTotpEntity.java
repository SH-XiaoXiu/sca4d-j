package com.sca4d.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** {@code iam_totp} 保存的加密 TOTP 密钥和最近已接受时间步。 */
@Data
@Accessors(chain = true)
@Table("iam_totp")
public class IamTotpEntity {
    @Id
    private Long credentialId;
    private String secretKeyId;
    private byte[] secretNonce;
    private byte[] secretCiphertext;
    private Long lastAcceptedStep;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
