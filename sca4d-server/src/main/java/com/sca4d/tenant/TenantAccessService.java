package com.sca4d.tenant;

import com.sca4d.common.access.AccessAction;
import com.sca4d.tenant.policy.PlatformTenantAccess;
import com.sca4d.tenant.policy.TenantAccessGrant;
import com.sca4d.tenant.policy.TenantActionScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 读取和配置 PLATFORM Principal 的租户 ACL，并解析本次请求允许访问的租户。 */
public interface TenantAccessService {

    /**
     * 将一个 Principal 的完整规则解析成指定最低等级可访问的租户集合。
     *
     * @param principalId PLATFORM Principal 主键
     * @param requiredAction 当前接口要求的最低访问等级
     * @return 已计算包含或排除集合的请求级范围
     */
    @NotNull
    TenantActionScope scope(@NotNull Long principalId, @NotNull AccessAction requiredAction);

    /**
     * 读取目标 PLATFORM Principal 的完整租户 ACL。
     *
     * @param targetPrincipalId 目标 PLATFORM Principal 主键
     * @return 完整租户 ACL 与乐观锁版本
     */
    @NotNull
    PlatformTenantAccess configuration(@NotNull Long targetPrincipalId);

    /**
     * 原子替换目标 PLATFORM Principal 的完整租户 ACL。
     *
     * @param targetPrincipalId 目标 PLATFORM Principal 主键
     * @param actorPrincipalId 当前执行 PLATFORM Principal 主键
     * @param defaultAction 全部租户默认最高等级；空值表示只允许显式租户
     * @param grants 具体租户覆盖规则
     * @param version 目标 PLATFORM Principal 当前版本
     */
    void replace(
            @NotNull Long actorPrincipalId,
            @NotNull Long targetPrincipalId,
            @Nullable AccessAction defaultAction,
            @NotNull List<TenantAccessGrant> grants,
            @NotNull Long version);
}
