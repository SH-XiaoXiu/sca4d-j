package com.sca4d.iam.resource;

import com.sca4d.common.auth.AccountScopeType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 操作日志和跨模块链接使用的 Account 安全投影。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountReference {
    private Long accountId;
    private String displayName;
    private AccountScopeType scopeType;
    private Long scopeId;
    private boolean deleted;
}
