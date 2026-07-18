package com.sca4d.tenant.policy;

import com.sca4d.common.access.AccessAction;
import com.sca4d.common.access.Policy;
import com.sca4d.common.access.ResourceAccessDeniedException;
import com.sca4d.common.access.ResourceAccessPolicy;
import com.sca4d.common.access.Rules;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.tenant.TenantAccessService;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/** 平台代管和租户自管操作共用的租户归属资源访问范围策略。 */
@Component
@RequiredArgsConstructor
public class TenantAccessPolicy implements ResourceAccessPolicy<TenantResource, TenantAccessCriteria> {

    private static final Policy<TenantResource> POLICY = Rules.policy(
            "tenant-access",
            TenantResource.class,
            "/cedar/tenant-access/schema.cedar",
            List.of(
                    Rules.<TenantResource>principal(PrincipalType.SYSTEM),
                    Rules.tenant(TenantResource::getTenantId, TenantResource::isCollection),
                    Rules.all(
                            Rules.<TenantResource>principal(PrincipalType.PLATFORM),
                            Rules.grant(TenantResource::isGranted))),
            List.of());

    private final TenantAccessService tenantAccessService;

    /** @return 租户资源关系使用的 Cedar Policy */
    @Override
    public @NotNull Policy<TenantResource> policy() {
        return POLICY;
    }

    /** @return 租户及其归属资源 Repository 必须消费的 Criteria 类型 */
    @Override
    public @NotNull Class<TenantAccessCriteria> criteriaType() {
        return TenantAccessCriteria.class;
    }

    /**
     * 读取当前 Principal 在指定访问等级下的租户资源。
     *
     * <p>PLATFORM Principal 每次请求固定读取一次 tenant_access_grant；TENANT 和 SYSTEM Principal 只使用
     * 当前请求中的 Principal 类型和 tenantId。DEFAULT Principal 返回空范围，并由 Cedar 拒绝。
     *
     * @param request 当前共用请求态
     * @param action 接口声明的访问等级
     * @param resourceId 可选租户主键
     * @return Cedar 判断和 Criteria 生成共用的租户资源
     */
    @Override
    public @NotNull TenantResource resource(
            @NotNull CurrentRequest request,
            @NotNull AccessAction action,
            @Nullable Object resourceId) {
        Long targetTenantId = targetTenantId(resourceId);
        if (request.system()) {
            return new TenantResource(
                    targetTenantId, new TenantActionScope(true, Set.of(), Set.of()));
        }
        Principal principal = request.requirePrincipal();
        if (principal.getPrincipalType() == PrincipalType.PLATFORM) {
            TenantActionScope scope = tenantAccessService.scope(principal.requirePrincipalId(), action);
            return new TenantResource(targetTenantId, scope);
        }
        if (principal.getPrincipalType() == PrincipalType.TENANT && principal.getTenantId() != null) {
            return new TenantResource(
                    targetTenantId,
                    new TenantActionScope(false, Set.of(principal.getTenantId()), Set.of()));
        }
        return new TenantResource(
                targetTenantId, new TenantActionScope(false, Set.of(), Set.of()));
    }

    /**
     * 将 Cedar 已允许的租户资源转换为 Repository Criteria。
     *
     * @param request 当前请求
     * @param action 接口声明的访问等级
     * @param resource 已通过 Cedar 判断的租户资源
     * @return Repository 必须消费的租户范围
     */
    @Override
    public @NotNull TenantAccessCriteria criteria(
            @NotNull CurrentRequest request,
            @NotNull AccessAction action,
            @NotNull TenantResource resource) {
        TenantActionScope scope = resource.getScope();
        return new TenantAccessCriteria(
                scope.isAllData(),
                action,
                scope.getTenantIds(),
                scope.getExcludedTenantIds(),
                resource.getTenantId());
    }

    /**
     * 将注解传入的单资源标识转换为租户主键。
     *
     * @param resourceId Access 注解解析出的资源标识；集合访问时为 null
     * @return 已校验的租户主键；集合访问时为 null
     */
    private @Nullable Long targetTenantId(@Nullable Object resourceId) {
        if (resourceId == null) {
            return null;
        }
        try {
            long tenantId = Long.parseLong(resourceId.toString());
            if (tenantId <= 0) {
                throw new NumberFormatException();
            }
            return tenantId;
        } catch (NumberFormatException exception) {
            throw new ResourceAccessDeniedException();
        }
    }
}
