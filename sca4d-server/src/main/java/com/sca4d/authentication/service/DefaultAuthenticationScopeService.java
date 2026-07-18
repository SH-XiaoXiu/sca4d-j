package com.sca4d.authentication.service;

import com.sca4d.authentication.AuthenticationScope;
import com.sca4d.authentication.AuthenticationScopeService;
import com.sca4d.authentication.error.AuthenticationErrorCode;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.exception.BizException;
import com.sca4d.tenant.Tenant;
import com.sca4d.tenant.TenantService;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

/** 校验匿名认证入口并使用当前有效租户编码取得内部租户主键。 */
@Service
@RequiredArgsConstructor
public class DefaultAuthenticationScopeService implements AuthenticationScopeService {

    private final TenantService tenants;

    @Override
    public @NotNull AuthenticationScope resolve(
            @Nullable AccountScopeType scopeType, @Nullable String tenantCode) {
        if (scopeType == AccountScopeType.GLOBAL && tenantCode == null) {
            return new AuthenticationScope(AccountScopeType.GLOBAL, null);
        }
        if (scopeType != AccountScopeType.TENANT || tenantCode == null) {
            throw new BizException(AuthenticationErrorCode.AUTHENTICATION_SCOPE_INVALID);
        }
        // 租户编码是公开入口标识；Flowable 只保存查询得到的租户主键，后续编码修改不影响已经创建的认证。
        Tenant tenant = tenants.requireActiveByCode(tenantCode);
        return new AuthenticationScope(AccountScopeType.TENANT, tenant.getTenantId());
    }
}
