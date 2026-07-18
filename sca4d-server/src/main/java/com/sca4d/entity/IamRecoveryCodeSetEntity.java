package com.sca4d.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** {@code iam_recovery_code_set} 保存的 Account 恢复码集合。 */
@Data
@Accessors(chain = true)
public class IamRecoveryCodeSetEntity {

    private Long id;
    private Long accountId;
    private OffsetDateTime issuedAt;
    private OffsetDateTime replacedAt;
    private Long version;
}
