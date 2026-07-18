package com.sca4d.iam.recovery.repository;

import lombok.Getter;
import lombok.Setter;

/** 当前恢复码集合中一条未使用摘要的数据库查询结果。 */
@Getter
@Setter
public class RecoveryCodeCandidateRow {

    private Long codeId;
    private Long setId;
    private Long accountId;
    private String codeHash;
    private Long setVersion;
}
