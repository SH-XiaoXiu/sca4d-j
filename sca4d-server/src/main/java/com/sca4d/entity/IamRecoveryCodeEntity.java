package com.sca4d.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** {@code iam_recovery_code} 保存的单个恢复码摘要和使用时间。 */
@Getter
@Setter
@Accessors(chain = true)
public class IamRecoveryCodeEntity {

    private Long id;
    private Long setId;
    private String codeHash;
    private OffsetDateTime createdAt;
    private OffsetDateTime usedAt;
}
