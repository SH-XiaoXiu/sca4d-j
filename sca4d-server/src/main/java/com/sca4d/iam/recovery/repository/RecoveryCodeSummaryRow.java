package com.sca4d.iam.recovery.repository;

import lombok.Data;

import java.time.OffsetDateTime;

/** 当前恢复码集合及未使用数量的数据库查询结果。 */
@Data
public class RecoveryCodeSummaryRow {

    private Long setId;
    private Long accountId;
    private OffsetDateTime issuedAt;
    private Long version;
    private Integer remainingCount;
}
