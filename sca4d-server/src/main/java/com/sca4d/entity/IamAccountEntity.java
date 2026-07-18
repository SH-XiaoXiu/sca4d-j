package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.iam.account.AccountStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** {@code iam_account} 保存的登录账号事实。 */
@Data
@Accessors(chain = true)
@Table("iam_account")
public class IamAccountEntity {
    private Long id;
    private Long subjectId;
    private AccountScopeType scopeType;
    private Long scopeId;
    private AccountStatus status;
    private Boolean secondFactorRequired;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
    private Integer deleted;
}
