package com.sca4d.iam.rbac;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 读取和修改一个 RBAC 领域中各角色空间的角色、权限选择符和 Principal 角色关系。 */
public interface RbacRepository {

    /** @return 用于选择当前 Repository 的 RBAC 领域 */
    @CheckReturnValue
    @NotNull
    RbacDomain domain();

    /** @return 指定角色空间中按编码排序的全部角色 */
    @CheckReturnValue
    @NotNull
    @Unmodifiable
    List<@NotNull Role> findRoles(@NotNull RbacScope scope);

    /** @return 指定角色空间中匹配标识的角色；不存在时为空 */
    @CheckReturnValue
    @NotNull
    Optional<Role> findRole(@NotNull RbacScope scope, @NotNull Long roleId);

    /** @return 指定角色空间中匹配编码的角色；不存在时为空 */
    @CheckReturnValue
    @NotNull
    Optional<Role> findRole(@NotNull RbacScope scope, @NotNull String code);

    /** @return 指定角色空间中匹配标识并按编码排序的角色 */
    @CheckReturnValue
    @NotNull
    @Unmodifiable
    List<@NotNull Role> findRoles(
            @NotNull RbacScope scope,
            @NotNull Collection<@NotNull Long> roleIds);

    /** 原子保存角色及初始权限选择符。 */
    @CheckReturnValue
    @NotNull
    Role createRole(
            @NotNull RbacScope scope,
            @NotNull Long roleId,
            @NotNull String code,
            @NotNull RoleStatus status,
            boolean builtIn,
            @NotNull Collection<@NotNull PermissionSelector> permissionSelectors);

    /** 把角色绑定给 Principal；已有受保护关系不能降级为普通关系。 */
    void bindRole(
            @NotNull RbacScope scope,
            @NotNull Long principalId,
            @NotNull Long roleId,
            boolean protectedRelation);

    /** @return 修改状态后的角色 */
    @CheckReturnValue
    @NotNull
    Role updateRoleStatus(
            @NotNull RbacScope scope,
            @NotNull Long roleId,
            @NotNull RoleStatus status);

    /** 物理删除指定角色空间中没有 Principal 使用的角色。 */
    void deleteRole(@NotNull RbacScope scope, @NotNull Long roleId);

    /** @return 指定角色保存并按编码排序的权限选择符 */
    @CheckReturnValue
    @NotNull
    @Unmodifiable
    List<@NotNull PermissionSelector> findPermissionSelectors(
            @NotNull RbacScope scope,
            @NotNull Long roleId);

    /** 原子替换角色权限选择符并增加角色版本。 */
    @CheckReturnValue
    @NotNull
    Role replacePermissionSelectors(
            @NotNull RbacScope scope,
            @NotNull Long roleId,
            @NotNull Collection<@NotNull PermissionSelector> permissionSelectors);

    /**
     * 一次读取同一角色空间中多个 Principal 的角色关系和集合版本。
     *
     * @return 每个输入 Principal 对应的角色结果；没有角色时仍包含空角色结果
     */
    @CheckReturnValue
    @NotNull
    @Unmodifiable
    Map<@NotNull Long, @NotNull PrincipalRoles> findPrincipalRoles(
            @NotNull RbacScope scope,
            @NotNull Collection<@NotNull Long> principalIds);

    /** 原子替换 Principal 的非保护角色并增加集合版本。 */
    @CheckReturnValue
    @NotNull
    PrincipalRoles replacePrincipalRoles(
            @NotNull RbacScope scope,
            @NotNull Long principalId,
            @NotNull Collection<@NotNull Long> roleIds);

    /**
     * 原子批量增加或移除一个角色的 Principal 关系，并保留每个 Principal 的其他角色。
     *
     * <p>实现必须批量锁定和写入，不能循环调用单 Principal 替换操作。受保护关系不能移除；
     * 已存在的添加关系和不存在的移除关系保持不变。
     *
     * @param scope 角色空间
     * @param roleId 目标角色标识
     * @param addPrincipalIds 需要增加目标角色的 Principal 标识
     * @param removePrincipalIds 需要移除目标角色的 Principal 标识
     * @return 按标识排序的实际变化 Principal；没有变化时返回空列表
     */
    @CheckReturnValue
    @NotNull
    @Unmodifiable
    List<@NotNull Long> changeRoleAssignments(
            @NotNull RbacScope scope,
            @NotNull Long roleId,
            @NotNull Collection<@NotNull Long> addPrincipalIds,
            @NotNull Collection<@NotNull Long> removePrincipalIds);

    /** @return 指定角色空间中按标识排序的角色使用者 */
    @CheckReturnValue
    @NotNull
    @Unmodifiable
    List<@NotNull Long> findRolePrincipalIds(@NotNull RbacScope scope, @NotNull Long roleId);

    /**
     * 一次读取当前领域中多个角色空间内启用角色贡献的权限选择符。
     *
     * @return 每个输入角色空间和 Principal 对应的选择符；没有授权时仍包含空列表
     */
    @CheckReturnValue
    @NotNull
    @Unmodifiable
    Map<@NotNull RbacScope, @NotNull Map<@NotNull Long, @NotNull List<@NotNull PermissionSelector>>> findActivePermissionSelectors(
            @NotNull Map<@NotNull RbacScope, ? extends Collection<@NotNull Long>> principalIdsByScope);
}
