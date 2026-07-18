package com.sca4d.common.auth;

import com.sca4d.common.exception.CommonErrorCode;
import com.sca4d.common.request.CurrentRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * 从当前 Session 读取并返回 DEFAULT Principal。
 *
 * <p>该选择器只读取当前请求已经恢复的完整 Session，不接受外部 Principal、Tenant 或业务选择键。
 */
public class DefaultPrincipalSelection implements PrincipalSelection<Map<String, Object>> {

    @Override
    public @NotNull Class<Map<String, Object>> parameterType() {
        return PrincipalSelection.mapParameterType();
    }

    @Override
    public @NotNull Principal select(
            @NotNull CurrentRequest request,
            @NotNull PrincipalType[] allowedTypes,
            @Nullable Long principalId,
            @Nullable Long tenantId,
            @Nullable String selectionKey,
            @NotNull Map<String, Object> parameters) {
        Subject subject = request.requireSubject();
        if (allowedTypes.length > 1
                || (allowedTypes.length == 1 && allowedTypes[0] != PrincipalType.DEFAULT)) {
            throw new ForbiddenException(CommonErrorCode.PRINCIPAL_SELECTION_REQUIRED);
        }
        if (principalId != null || tenantId != null || selectionKey != null) {
            throw new ForbiddenException(CommonErrorCode.PRINCIPAL_SELECTION_DEFAULT_EXTERNAL_INPUT_FORBIDDEN);
        }
        for (Principal candidate : request.requireSession().getPrincipals()) {
            if (candidate.getPrincipalType() == PrincipalType.DEFAULT
                    && subject.getSubjectId().equals(candidate.getSubjectId())
                    && candidate.getTenantId() == null) {
                return candidate;
            }
        }
        throw new ForbiddenException(CommonErrorCode.AUTHENTICATION_DEFAULT_PRINCIPAL_UNAVAILABLE);
    }
}
