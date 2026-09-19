package com.sca4d.iam.base.mapper;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.iam.identity.PrincipalType;
import com.sca4d.iam.rbac.RoleStatus;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/** 持久化 Platform RBAC 的角色、权限和主体角色关系。 */
public interface PlatformRbacMapper {
    /** 按可选标识集合和编码读取 Platform 角色；forUpdate 在当前事务持有角色行锁。 */
    @CheckReturnValue
    @NotNull
    List<PlatformRoleState> selectPlatformRoles(
            @Param("roleIds") @Nullable Collection<Long> roleIds,
            @Param("code") @Nullable String code,
            @Param("forUpdate") boolean forUpdate);

    /**
     * 新增 Platform 角色。
     *
     * @param roleId 角色标识
     * @param code 唯一且创建后不可修改的角色编码
     * @param i18n 完整多语言字段
     * @param status 初始状态
     * @param builtIn 是否为内置角色
     * @return 新增行数
     */
    int insertPlatformRole(
            @Param("roleId") @NotNull Long roleId,
            @Param("code") @NotNull String code,
            @Param("i18n") @NotNull LocalizedFields i18n,
            @Param("status") @NotNull RoleStatus status,
            @Param("builtIn") boolean builtIn);

    /** 按统一版本更新 Platform 角色的状态和多语言字段。 */
    @CheckReturnValue
    int updatePlatformRole(
            @Param("roleId") @NotNull Long roleId,
            @Param("status") @NotNull Patch<RoleStatus> status,
            @Param("i18n") @NotNull Patch<LocalizedFields> i18n,
            @Param("expectedVersion") @NotNull Version expectedVersion);

    /**
     * 物理删除 Platform 角色。
     *
     * @param roleId 角色标识
     * @param expectedVersion 预期版本
     * @return 删除行数；角色不存在或版本不匹配时为零
     */
    int deletePlatformRole(
            @Param("roleId") @NotNull Long roleId,
            @Param("expectedVersion") @NotNull Version expectedVersion);

    /**
     * 原子增加 Platform 角色统一版本。
     *
     * @param roleId 角色标识
     * @param expectedVersion 预期版本
     * @return 修改行数；角色不存在或版本不匹配时为零
     */
    @CheckReturnValue
    int updatePlatformRoleVersion(
            @Param("roleId") @NotNull Long roleId,
            @Param("expectedVersion") @NotNull Version expectedVersion);

    /**
     * 查询 Platform 角色保存的 Operation 选择符。
     *
     * @param roleId 角色标识
     * @return 按编码排序的具体 ACTION 编码或内部 {@code "*"} 选择符
     */
    @CheckReturnValue
    @NotNull
    List<@NotNull String> selectPlatformPermissionSelectors(
            @Param("roleId") @NotNull Long roleId);

    /**
     * 删除角色的全部 Operation 选择符。
     *
     * @param roleId 角色标识
     * @return 删除行数
     */
    int deletePlatformPermissionSelectors(@Param("roleId") @NotNull Long roleId);

    /**
     * 批量新增角色 Operation 选择符。
     *
     * @param roleId 角色标识
     * @param permissionSelectors 具体 ACTION 编码或内部 {@code "*"} 选择符
     * @return 新增行数
     */
    int insertPlatformPermissionSelectors(
            @Param("roleId") @NotNull Long roleId,
            @Param("permissionSelectors") @NotNull Collection<@NotNull String> permissionSelectors);

    /**
     * 读取 PlatformPrincipal 角色集合版本。
     *
     * @param principalId PlatformPrincipal 标识
     * @return 当前版本；Principal 不存在时为空
     */
    @CheckReturnValue
    @Nullable
    Version selectPlatformPrincipalRolesVersion(
            @Param("principalId") @NotNull Long principalId);

    /**
     * 批量读取 PlatformPrincipal 当前角色关系。
     *
     * @param principalIds PlatformPrincipal 标识
     * @return 按 Principal 标识和角色编码排序的角色关系
     */
    @CheckReturnValue
    @NotNull
    List<@NotNull PlatformPrincipalRole> selectPlatformPrincipalRoles(
            @Param("principalIds") @NotNull Collection<@NotNull Long> principalIds);

    /**
     * 原子增加 PlatformPrincipal 角色集合版本。
     *
     * @param principalId PlatformPrincipal 标识
     * @param expectedVersion 预期版本
     * @return 修改行数；Principal 不存在或版本不匹配时为零
     */
    @CheckReturnValue
    int updatePlatformPrincipalRolesVersion(
            @Param("principalId") @NotNull Long principalId,
            @Param("expectedVersion") @NotNull Version expectedVersion);

    /**
     * 删除 Principal 的角色关系；protectedRelation 为空时包含受保护关系。
     *
     * @param principalId PlatformPrincipal 标识
     * @return 删除行数
     */
    int deletePlatformPrincipalRoles(
            @Param("principalId") @NotNull Long principalId,
            @Param("protectedRelation") @Nullable Boolean protectedRelation);

    /**
     * 批量新增 Principal 的非保护角色关系。
     *
     * @param principalId PlatformPrincipal 标识
     * @param roleIds 角色标识
     * @return 新增行数
     */
    int insertPlatformPrincipalRoles(
            @Param("principalId") @NotNull Long principalId,
            @Param("roleIds") @NotNull Collection<@NotNull Long> roleIds);

    /**
     * 按标识顺序锁定未删除的 PlatformPrincipal。
     *
     * @param principalIds Principal 标识
     * @param principalType PlatformPrincipal 持久化类型
     * @return 实际锁定的 Principal 标识
     */
    @CheckReturnValue
    @NotNull
    List<@NotNull Long> lockPlatformPrincipals(
            @Param("principalIds") @NotNull Collection<@NotNull Long> principalIds,
            @Param("principalType") @NotNull PrincipalType principalType);

    /**
     * 批量查询不能由普通管理操作移除的目标角色关系。
     *
     * @param roleId 目标角色标识
     * @param principalIds Principal 标识
     * @return 按标识排序的受保护关系 Principal
     */
    @CheckReturnValue
    @NotNull
    List<@NotNull Long> selectProtectedPlatformRolePrincipalIds(
            @Param("roleId") @NotNull Long roleId,
            @Param("principalIds") @NotNull Collection<@NotNull Long> principalIds);

    /**
     * 批量新增目标角色关系，并返回实际新增的 Principal。
     *
     * @param roleId 目标角色标识
     * @param principalIds Principal 标识
     * @return 新增关系的 Principal 标识
     */
    @CheckReturnValue
    @NotNull
    List<@NotNull Long> insertPlatformRoleAssignments(
            @Param("roleId") @NotNull Long roleId,
            @Param("principalIds") @NotNull Collection<@NotNull Long> principalIds);

    /**
     * 批量删除普通目标角色关系，并返回实际删除的 Principal。
     *
     * @param roleId 目标角色标识
     * @param principalIds Principal 标识
     * @return 删除关系的 Principal 标识
     */
    @CheckReturnValue
    @NotNull
    List<@NotNull Long> deletePlatformRoleAssignments(
            @Param("roleId") @NotNull Long roleId,
            @Param("principalIds") @NotNull Collection<@NotNull Long> principalIds);

    /**
     * 为实际发生角色关系变化的 PlatformPrincipal 批量递增集合版本。
     *
     * @param principalIds 实际发生关系变化的 Principal 标识
     * @return 修改行数
     */
    @CheckReturnValue
    int incrementPlatformPrincipalRolesVersions(
            @Param("principalIds") @NotNull Collection<@NotNull Long> principalIds);

    /**
     * 新增或保留 Principal 角色关系；{@code protectedRelation} 为真时把已有关系改为受保护关系。
     *
     * @param principalId PlatformPrincipal 标识
     * @param roleId 角色标识
     * @param protectedRelation 是否禁止普通角色替换删除这条关系
     * @return 新增或修改行数
     */
    int upsertPlatformPrincipalRole(
            @Param("principalId") @NotNull Long principalId,
            @Param("roleId") @NotNull Long roleId,
            @Param("protectedRelation") boolean protectedRelation);

    /**
     * 查询使用指定 Platform 角色的 Principal。
     *
     * @param roleId 角色标识
     * @return 按标识排序的关联 Principal
     */
    @CheckReturnValue
    @NotNull
    List<@NotNull Long> selectPlatformRolePrincipalIds(
            @Param("roleId") @NotNull Long roleId);

    /**
     * 批量查询启用角色为 PlatformPrincipal 提供的 Operation 选择符。
     *
     * @param principalIds PlatformPrincipal 标识
     * @return 按 Principal 和选择符排序的查询结果
     */
    @CheckReturnValue
    @NotNull
    List<@NotNull PlatformPrincipalPermission> selectPlatformActiveOperations(
            @Param("principalIds") @NotNull Collection<@NotNull Long> principalIds);

}
