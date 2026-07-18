package com.sca4d.authentication.me;

import com.sca4d.authentication.error.AuthenticationErrorCode;
import com.sca4d.common.auth.ForbiddenException;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalSelection;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.enums.EnumValueCodec;
import com.sca4d.common.request.CurrentRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * 为当前用户启动状态选择 DEFAULT、PLATFORM 或 TENANT Principal。
 */
@Component
public class MePrincipalSelection implements PrincipalSelection<Map<String, Object>> {

    /**
     * 声明该选择器直接消费请求变量映射。
     *
     * @return PrincipalSelection 通用请求变量类型
     */
    @Override
    public @NotNull Class<Map<String, Object>> parameterType() {
        return PrincipalSelection.mapParameterType();
    }

    /**
     * 根据当前 Subject 和显式主体类型选择启动状态使用的 Principal。
     *
     * @param request 当前共用请求态
     * @param allowedTypes 接口允许的主体类型；当前接口不额外限制时为空
     * @param principalId 外部 Principal 主键；当前用户接口不接受该值
     * @param tenantId TENANT 主体所属租户；其他主体类型不接受该值
     * @param selectionKey PrincipalType 固定编码；缺失时使用 DEFAULT
     * @param parameters 当前请求变量映射
     * @return 属于当前 Subject 且满足可用状态的 Principal
     */
    @Override
    public @NotNull Principal select(
            @NotNull CurrentRequest request,
            @NotNull PrincipalType[] allowedTypes,
            @Nullable Long principalId,
            @Nullable Long tenantId,
            @Nullable String selectionKey,
            @NotNull Map<String, Object> parameters) {
        if (principalId != null) {
            throw new ForbiddenException(AuthenticationErrorCode.ME_EXTERNAL_PRINCIPAL_ID_FORBIDDEN);
        }
        PrincipalType principalType = selectionKey == null
                ? PrincipalType.defaultType()
                : EnumValueCodec.decode(PrincipalType.class, selectionKey);
        if (principalType == null || principalType == PrincipalType.SYSTEM) {
            throw new ForbiddenException(AuthenticationErrorCode.ME_SYSTEM_PRINCIPAL_FORBIDDEN);
        }
        request.requireSubject();
        if (principalType == PrincipalType.DEFAULT) {
            if (tenantId != null) {
                throw new ForbiddenException(AuthenticationErrorCode.ME_DEFAULT_TENANT_ID_FORBIDDEN);
            }
            return requireFromSession(request, PrincipalType.DEFAULT, null);
        }
        if (principalType == PrincipalType.PLATFORM) {
            if (tenantId != null) {
                throw new ForbiddenException(AuthenticationErrorCode.ME_PLATFORM_TENANT_ID_FORBIDDEN);
            }
            return requireFromSession(request, PrincipalType.PLATFORM, null);
        }
        if (tenantId == null) {
            throw new ForbiddenException(AuthenticationErrorCode.ME_TENANT_ID_REQUIRED);
        }
        return requireFromSession(request, PrincipalType.TENANT, tenantId);
    }

    /**
     * 从当前会话快照选择指定范围的 Principal。
     *
     * @param request 当前请求
     * @param principalType 需要选择的 Principal 类型
     * @param tenantId TENANT 主体的租户主键
     * @return 当前 Subject 可用的匹配 Principal
     */
    private @NotNull Principal requireFromSession(
            @NotNull CurrentRequest request,
            @NotNull PrincipalType principalType,
            @Nullable Long tenantId) {
        for (Principal candidate : request.requireSession().getPrincipals()) {
            if (candidate.getPrincipalType() == principalType
                    && Objects.equals(candidate.getTenantId(), tenantId)) {
                return candidate;
            }
        }
        throw new ForbiddenException(AuthenticationErrorCode.ME_PRINCIPAL_UNAVAILABLE, principalType.value());
    }
}
