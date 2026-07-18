package com.sca4d.iam.principal;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalSelection;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.UnauthenticatedException;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.tenant.TenantErrorCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;

/**
 * 按路由 tenantId 从当前 Subject 的会话快照选择 TENANT Principal。
 *
 * <p>默认实现约定 Controller 使用 {@code tenantId = "#path.tenantId"}，普通请求不查询数据库。需要从
 * header、query、body 或其他数据选择租户的业务使用独立 {@link PrincipalSelection}，并明确来源可信度、
 * 缓存失效和审计含义。
 */
@Component
public class TenantPrincipalSelection implements PrincipalSelection<Map<String, Object>> {

    @Override
    public @NotNull Class<Map<String, Object>> parameterType() {
        return PrincipalSelection.mapParameterType();
    }

    @Override
    public @NotNull Principal select(
            @NotNull CurrentRequest auth,
            @NotNull PrincipalType[] allowedTypes,
            @Nullable Long principalId,
            @Nullable Long tenantId,
            @Nullable String selectionKey,
            @NotNull Map<String, Object> parameters) {
        if (allowedTypes.length > 0 && Arrays.stream(allowedTypes).noneMatch(type -> type == PrincipalType.TENANT)) {
            throw new BizException(TenantErrorCode.TENANT_PRINCIPAL_NOT_ALLOWED);
        }
        if (tenantId == null) {
            throw new BizException(TenantErrorCode.TENANT_ID_REQUIRED);
        }
        Subject subject = auth.getSubject();
        if (subject == null) {
            throw new UnauthenticatedException();
        }
        for (Principal candidate : auth.requireSession().getPrincipals()) {
            if (candidate.getPrincipalType() == PrincipalType.TENANT
                    && tenantId.equals(candidate.getTenantId())) {
                if (principalId != null && !principalId.equals(candidate.getPrincipalId())) {
                    throw new BizException(TenantErrorCode.FOREIGN_TENANT_PRINCIPAL);
                }
                return candidate;
            }
        }
        throw new BizException(TenantErrorCode.TENANT_MEMBERSHIP_UNAVAILABLE);
    }
}
