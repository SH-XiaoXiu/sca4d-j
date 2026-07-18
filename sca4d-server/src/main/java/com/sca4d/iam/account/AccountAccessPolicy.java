package com.sca4d.iam.account;

import com.sca4d.common.access.ResourceAccess;
import com.sca4d.common.access.ResourceAccessDeniedException;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.exception.BizException;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.tenant.policy.TenantAccessCriteria;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/** 校验当前 Principal 是否可以管理目标 Account 的安全资源。 */
@Component
@RequiredArgsConstructor
public class AccountAccessPolicy {

    private final ResourceAccess resourceAccess;

    /**
     * 确认当前 Principal 可以访问目标 Account。
     *
     * <p>DEFAULT 只能访问同 Subject Account；TENANT 只能访问当前租户 TENANT Account；PLATFORM 可访问
     * GLOBAL Account，访问 TENANT Account 时还必须落在当前租户访问条件内。
     */
    public void require(@NotNull Principal principal, @NotNull IamAccountEntity account) {
        PrincipalType type = principal.requirePrincipalType();
        if (type == PrincipalType.DEFAULT
                && principal.getSubjectId() != null
                && principal.getSubjectId().equals(account.getSubjectId())) {
            return;
        }
        if (type == PrincipalType.TENANT
                && account.getScopeType() == AccountScopeType.TENANT
                && principal.getTenantId() != null
                && principal.getTenantId().equals(account.getScopeId())) {
            return;
        }
        if (type == PrincipalType.PLATFORM && account.getScopeType() == AccountScopeType.GLOBAL) {
            return;
        }
        if (type == PrincipalType.PLATFORM && account.getScopeType() == AccountScopeType.TENANT) {
            TenantAccessCriteria criteria = resourceAccess.require(TenantAccessCriteria.class);
            Long tenantId = account.getScopeId();
            if (tenantId != null && (criteria.isAllData()
                    ? !criteria.getExcludedTenantIds().contains(tenantId)
                    : criteria.getTenantIds().contains(tenantId))) {
                return;
            }
        }
        throw new ResourceAccessDeniedException();
    }

    /**
     * 确认目标 Account 属于指定租户，并且当前 Principal 可以管理其安全资源。
     *
     * <p>路径租户与 Account 作用域不一致时按资源不存在处理，避免跨租户探测 Account 主键。
     */
    public void requireTenantAccount(
            @NotNull Principal principal, @NotNull Long tenantId, @NotNull IamAccountEntity account) {
        if (account.getScopeType() != AccountScopeType.TENANT || !tenantId.equals(account.getScopeId())) {
            throw new BizException(IamErrorCode.ACCOUNT_NOT_FOUND);
        }
        require(principal, account);
    }
}
