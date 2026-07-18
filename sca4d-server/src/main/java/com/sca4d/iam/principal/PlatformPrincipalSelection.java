package com.sca4d.iam.principal;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalSelection;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.UnauthenticatedException;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.iam.error.IamErrorCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;

/** 按当前 Subject 选择 PLATFORM Principal。 */
@Component
public class PlatformPrincipalSelection implements PrincipalSelection<Map<String, Object>> {

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
        if (allowedTypes.length > 0 && Arrays.stream(allowedTypes).noneMatch(type -> type == PrincipalType.PLATFORM)) {
            throw new BizException(IamErrorCode.PLATFORM_PRINCIPAL_NOT_ALLOWED);
        }
        Subject subject = auth.getSubject();
        if (subject == null) {
            throw new UnauthenticatedException();
        }
        for (Principal candidate : auth.requireSession().getPrincipals()) {
            if (candidate.getPrincipalType() == PrincipalType.PLATFORM) {
                if (principalId != null && !principalId.equals(candidate.getPrincipalId())) {
                    throw new BizException(IamErrorCode.FOREIGN_PLATFORM_PRINCIPAL);
                }
                return candidate;
            }
        }
        throw new BizException(IamErrorCode.PLATFORM_PRINCIPAL_UNAVAILABLE);
    }
}
