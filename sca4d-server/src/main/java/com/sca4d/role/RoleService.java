package com.sca4d.role;

import com.sca4d.common.web.PageData;
import com.sca4d.iam.principal.RoleStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 在指定平台或租户内创建、查询、修改和删除角色，并替换角色权限。 */
public interface RoleService {

    /** 分页读取角色及其使用量。 */
    @NotNull
    PageData<ManagedRole> page(
            @Nullable Long tenantId,
            @Nullable String search,
            @Nullable RoleStatus status,
            long page,
            long size);

    /** 读取一个角色、权限集合和使用量详情。 */
    @NotNull
    ManagedRole detail(@Nullable Long tenantId, @NotNull Long roleId);

    /** 读取当前角色范围允许分配的代码权限目录。 */
    @NotNull
    List<ManagedPermission> assignablePermissions(@Nullable Long tenantId);

    /** 创建自定义角色并写入初始权限集合。 */
    @NotNull
    Long create(
            @Nullable Long tenantId,
            @NotNull String code,
            @NotNull String name,
            @NotNull List<Long> permissionIds);

    /** 更新角色名称和状态。 */
    void update(
            @Nullable Long tenantId,
            @NotNull Long roleId,
            @NotNull String name,
            @NotNull RoleStatus status,
            long version);

    /** 替换自定义角色的全部权限。 */
    void replacePermissions(
            @Nullable Long tenantId,
            @NotNull Long roleId,
            @NotNull List<Long> permissionIds,
            long version);

    /** 删除没有 Principal 使用的自定义角色。 */
    void delete(@Nullable Long tenantId, @NotNull Long roleId, long version);
}
