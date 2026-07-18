package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.iam.identifier.AccountIdentifierStatus;
import com.sca4d.iam.identifier.AccountIdentifierType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** {@code iam_account_identifier} 保存的 Account 登录标识事实。 */
@Data
@Accessors(chain = true)
@Table("iam_account_identifier")
public class IamAccountIdentifierEntity {
    private Long id;
    private Long accountId;
    private AccountScopeType scopeType;
    private Long scopeId;
    private AccountIdentifierType identifierType;
    private String value;
    private String normalizedValue;
    private Long contactId;
    private AccountIdentifierStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
    private Integer deleted;
}
