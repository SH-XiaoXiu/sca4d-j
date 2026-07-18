package com.sca4d.iam.principal;

import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.web.PageData;
import com.sca4d.entity.IamPrincipalEntity;
import com.sca4d.tenant.MembershipStatus;
import com.sca4d.tenant.TenantRole;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** 创建和查询 Principal，并修改 Principal 状态与租户角色。 */
public interface PrincipalService {

    /**
     * 为 Subject 创建唯一 DEFAULT Principal。
     *
     * @param subjectId 所属 Subject 主键
     * @param displayName DEFAULT Principal 展示名称
     * @return 已创建的 DEFAULT Principal
     */
    @NotNull
    IamPrincipalEntity createDefault(@NotNull Long subjectId, @NotNull String displayName);

    /**
     * 为 Subject 创建 PLATFORM Principal。
     *
     * @param subjectId 所属 Subject 主键
     * @param displayName PLATFORM Principal 展示名称
     * @return 已创建的 PLATFORM Principal
     */
    @NotNull
    IamPrincipalEntity createPlatform(@NotNull Long subjectId, @NotNull String displayName);

    /**
     * 为既有用户创建默认不能访问任何租户的委托 PLATFORM Principal。
     *
     * @param subjectId 所属 Subject 主键
     * @param displayName PLATFORM Principal 展示名称
     * @return 已创建且尚未绑定角色和租户 ACL 的 PLATFORM Principal
     */
    @NotNull
    IamPrincipalEntity createDelegatedPlatform(@NotNull Long subjectId, @NotNull String displayName);

    /**
     * 为 Subject 加入一个租户并创建 TENANT Principal。
     *
     * @param subjectId 所属 Subject 主键
     * @param tenantId 目标租户主键
     * @param displayName 租户内展示名称
     * @param roleIds 初始租户角色主键集合
     * @return 已创建的 TENANT Principal
     * @throws com.sca4d.common.exception.BizException Subject 已是该租户成员时抛出
     */
    @NotNull
    IamPrincipalEntity createTenant(
            @NotNull Long subjectId,
            @NotNull Long tenantId,
            @NotNull String displayName,
            @NotNull List<Long> roleIds);

    /**
     * 读取一个存在且未删除的 Principal。
     *
     * @param principalId Principal 主键
     * @return 当前 Principal
     * @throws com.sca4d.common.exception.BizException Principal 不存在时抛出
     */
    @NotNull
    IamPrincipalEntity require(@NotNull Long principalId);

    /**
     * 按 Subject 和业务范围读取一个未删除的 Principal。
     *
     * @param subjectId Principal 所属 Subject 主键
     * @param principalType Principal 类型
     * @param tenantId TENANT Principal 的租户主键；其他类型传 null
     * @return 匹配业务范围的 Principal
     * @throws com.sca4d.common.exception.BizException Principal 不存在时抛出
     */
    @NotNull
    IamPrincipalEntity require(
            @NotNull Long subjectId,
            @NotNull PrincipalType principalType,
            @Nullable Long tenantId);

    /**
     * 判断系统是否已存在任意 PLATFORM Principal。
     *
     * @return 存在至少一个未删除的平台主体时返回 {@code true}
     */
    boolean hasPlatformPrincipal();

    /**
     * 批量读取 Subject 对应的平台主体和当前角色绑定。
     *
     * @param subjectIds Subject 主键集合
     * @return 按 Subject 主键索引的平台主体
     */
    @NotNull
    Map<Long, PlatformPrincipal> findPlatformPrincipals(@NotNull List<Long> subjectIds);

    /** 分页读取平台操作员及其当前平台角色，不返回认证材料。 */
    @NotNull
    PageData<PlatformPrincipal> pagePlatformPrincipals(long page, long size);

    /** 读取一个平台操作员及其当前平台角色，不返回认证材料。 */
    @NotNull
    PlatformPrincipal requirePlatformPrincipal(@NotNull Long principalId);

    /**
     * 读取一个 PLATFORM Principal 当前可绑定的平台角色候选。
     *
     * @param principalId PLATFORM Principal 主键
     * @return 当前启用的平台角色
     */
    @NotNull
    List<PlatformRole> assignablePlatformRoles(@NotNull Long principalId);

    /**
     * 读取可以绑定给指定租户 TENANT Principal 的启用角色。
     *
     * @param tenantId 租户主键
     * @return 按名称、编码和主键固定排序的租户角色选择项
     */
    @NotNull
    List<TenantRole> assignableTenantRoles(@NotNull Long tenantId);

    /**
     * 修改 Principal 的可用状态。
     *
     * @param principalId Principal 主键
     * @param status 目标状态
     * @param version 调用方读取到的乐观锁版本
     */
    void changeStatus(@NotNull Long principalId, @NotNull PrincipalStatus status, @NotNull Long version);

    /**
     * 修改 PLATFORM Principal 的可用状态。
     *
     * @param principalId PLATFORM Principal 主键
     * @param status 目标状态
     * @param version 当前 Principal 版本
     */
    void changePlatformStatus(
            @NotNull Long principalId,
            @NotNull PrincipalStatus status,
            @NotNull Long version);

    /**
     * 修改指定租户中的 TENANT Principal 状态。
     *
     * @param principalId TENANT Principal 主键
     * @param tenantId 所属租户主键
     * @param status 目标状态
     * @param version 调用方读取到的乐观锁版本
     */
    void changeTenantStatus(
            @NotNull Long principalId,
            @NotNull Long tenantId,
            @NotNull MembershipStatus status,
            @NotNull Long version);

    /**
     * 用租户内的角色集合替换 TENANT Principal 的全部角色绑定。
     *
     * @param principalId TENANT Principal 主键
     * @param tenantId 所属租户主键
     * @param roleIds 去重前的目标角色主键集合
     * @param version 调用方读取到的 Principal 版本
     */
    void replaceTenantRoles(
            @NotNull Long principalId,
            @NotNull Long tenantId,
            @NotNull List<Long> roleIds,
            @NotNull Long version);

    /**
     * 用平台角色集合替换一个 PLATFORM Principal 的当前绑定。
     *
     * @param principalId PLATFORM Principal 主键
     * @param roleIds 去重前的目标平台角色主键集合
     * @param version 调用方读取到的 PLATFORM Principal 版本
     */
    void replacePlatformRoles(@NotNull Long principalId, @NotNull List<Long> roleIds, @NotNull Long version);

    /**
     * 清除绑定指定角色的全部 Principal 会话权限缓存。
     *
     * @param roleId 权限关系已经变化的角色主键
     */
    void refreshRoleSessions(@NotNull Long roleId);

    /**
     * 清理指定租户全部成员会话中的可选择 Principal 快照。
     *
     * @param tenantId 状态已经变化的租户主键
     */
    void refreshTenantSessions(@NotNull Long tenantId);
}
