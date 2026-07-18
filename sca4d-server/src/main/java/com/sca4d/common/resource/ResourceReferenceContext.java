package com.sca4d.common.resource;

import com.sca4d.common.auth.PrincipalType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** 资源引用解析所需且已从 Session 装载的当前 Principal 权限数据。 */
public class ResourceReferenceContext {

    private final PrincipalType principalType;
    private final Long principalId;
    private final Long tenantId;
    private final Set<String> permissionCodes;

    /**
     * 创建不触发数据库回查的资源引用权限上下文。
     *
     * @param principalType 当前选择的 Principal 类型
     * @param principalId 当前请求已经选择的 Principal 主键
     * @param tenantId 当前 TENANT Principal 所属租户；平台和系统主体为 null
     * @param permissionCodes 当前 Session 已装载的权限编码
     */
    public ResourceReferenceContext(
            @NotNull PrincipalType principalType,
            @NotNull Long principalId,
            @Nullable Long tenantId,
            @NotNull Set<String> permissionCodes) {
        this.principalType = principalType;
        this.principalId = principalId;
        this.tenantId = tenantId;
        this.permissionCodes = Set.copyOf(permissionCodes);
    }

    /** @return 当前选择的 Principal 类型 */
    public @NotNull PrincipalType getPrincipalType() {
        return principalType;
    }

    /** @return 当前请求已经选择的 Principal 主键 */
    public @NotNull Long getPrincipalId() {
        return principalId;
    }

    /** @return 当前 TENANT Principal 所属租户；其他主体为 null */
    public @Nullable Long getTenantId() {
        return tenantId;
    }

    /** @return 当前 Session 已装载的不可变权限编码 */
    public @NotNull Set<String> getPermissionCodes() {
        return permissionCodes;
    }

    /**
     * 判断当前 Principal 是否具备详情权限。
     *
     * @param permissionCode 详情路由要求的代码权限
     * @return 权限集合包含该编码时返回 true
     */
    public boolean hasPermission(@NotNull String permissionCode) {
        return permissionCodes.contains(permissionCode);
    }
}
