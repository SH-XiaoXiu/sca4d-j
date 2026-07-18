package com.sca4d.mapper;

import com.mybatisflex.core.BaseMapper;
import com.sca4d.common.auth.CredentialType;
import com.sca4d.entity.IamPermissionEntity;
import com.sca4d.entity.IamPrincipalEntity;
import com.sca4d.entity.IamPrincipalRoleEntity;
import com.sca4d.entity.IamRoleEntity;
import com.sca4d.entity.IamRolePermissionEntity;
import com.sca4d.entity.IamSubjectEntity;
import com.sca4d.entity.IamTenantEntity;
import com.sca4d.entity.IamTenantMembershipEntity;
import com.sca4d.iam.cleanup.SubjectCleanupTask;
import com.sca4d.iam.cleanup.TenantCleanupTask;
import com.sca4d.iam.credential.CredentialStatus;
import com.sca4d.iam.principal.PlatformRole;
import com.sca4d.iam.principal.PrincipalStatus;
import com.sca4d.iam.principal.RoleStatus;
import com.sca4d.iam.repository.CredentialRow;
import com.sca4d.iam.subject.SubjectStatus;
import com.sca4d.iam.tenant.TenantStatus;
import com.sca4d.permission.repository.SessionRow;
import com.sca4d.role.repository.RoleUsageCount;
import com.sca4d.tenant.MembershipStatus;
import com.sca4d.tenant.TenantMembership;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/** IAM 持久化 Mapper。 */
@Mapper
public interface IamMapper extends BaseMapper<IamSubjectEntity> {

    /** @param subject 已分配主键并确定初始状态的 Subject 数据库事实 */
    void insertSubject(@Param("subject") @NotNull IamSubjectEntity subject);

    /**
     * 按乐观锁版本更新 Subject 资料字段。
     *
     * @param subjectId Subject 主键
     * @param displayName 新展示名称
     * @param version 当前乐观锁版本
     * @return 实际更新行数
     */
    int updateSubject(
            @Param("subjectId") @NotNull Long subjectId,
            @Param("displayName") @Nullable String displayName,
            @Param("version") @NotNull Long version);

    /**
     * 按乐观锁版本更新 Subject 的启用、停用或删除状态。
     *
     * @param subjectId Subject 主键
     * @param status 目标状态
     * @param version 当前乐观锁版本
     * @return 实际更新行数
     */
    int updateSubjectStatus(
            @Param("subjectId") @NotNull Long subjectId,
            @Param("status") @NotNull SubjectStatus status,
            @Param("version") @NotNull Long version);

    /**
     * 在用户聚合敏感数据变更前原子推进 Subject 版本。
     *
     * @param subjectId Subject 主键
     * @param version 调用方读取到的版本
     * @return 实际更新行数
     */
    int advanceSubjectVersion(
            @Param("subjectId") @NotNull Long subjectId,
            @Param("version") @NotNull Long version);

    /**
     * 根据版本条件将 Subject 原子标记为删除终态。
     *
     * @param subjectId 登录身份主键
     * @param version 调用方读取到的乐观锁版本
     * @return 实际更新行数
     */
    int deleteSubject(@Param("subjectId") @NotNull Long subjectId, @Param("version") @NotNull Long version);

    /**
     * 隔离指定 Subject 的 Account、Identifier、Credential 和 Contact。
     *
     * @param subjectId 登录身份主键
     */
    void isolateAccountsBySubject(@Param("subjectId") @NotNull Long subjectId);

    /** @return 排除指定 Account 后是否存在失去登录入口的 ACTIVE 非 DEFAULT Principal */
    boolean hasUnreachableActivePrincipal(
            @Param("subjectId") @NotNull Long subjectId,
            @Param("excludedAccountId") @NotNull Long excludedAccountId);

    /** @return 按版本隔离 Account 后实际更新的账号行数 */
    int isolateAccount(
            @Param("accountId") @NotNull Long accountId,
            @Param("version") long version);

    /**
     * 隔离指定 Subject 的全部 Principal。
     *
     * @param subjectId 登录身份主键
     */
    void deletePrincipalsBySubject(@Param("subjectId") @NotNull Long subjectId);

    /** @param subjectId 删除终态 Subject 主键 */
    void deleteMembershipsBySubject(@Param("subjectId") @NotNull Long subjectId);

    /**
     * 登记 Subject 关系清理任务，重复资源不会创建第二条任务。
     *
     * @param taskId 集群 ID 生成器分配的任务主键
     * @param subjectId 待清理登录身份主键
     */
    void insertSubjectCleanupTask(@Param("taskId") @NotNull Long taskId, @Param("subjectId") @NotNull Long subjectId);

    /**
     * 使用数据库锁原子领取一批待处理任务。
     *
     * @param limit 当前节点本轮最多领取数量
     * @return 已转换为 PROCESSING 状态的任务
     */
    @NotNull
    List<SubjectCleanupTask> claimPendingSubjectCleanupTasks(@Param("limit") int limit);

    /**
     * 硬删除已隔离 Subject 的 IAM 关系和认证明细。
     *
     * @param subjectId 已进入删除终态的登录身份主键
     */
    void deleteIsolatedSubjectRelations(@Param("subjectId") @NotNull Long subjectId);

    /**
     * 完成一个处于处理中的清理任务。
     *
     * @param taskId 清理任务主键
     */
    void completeCleanupTask(@Param("taskId") @NotNull Long taskId);

    /**
     * 释放失败任务并保存错误摘要。
     *
     * @param taskId 清理任务主键
     * @param error 最近一次失败摘要
     */
    void releaseCleanupTask(@Param("taskId") @NotNull Long taskId, @Param("error") @NotNull String error);

    /**
     * 原子领取一批待执行或租约超时的 Tenant 清理任务。
     *
     * @param limit 本轮领取上限
     * @return 已推进到 PROCESSING 的任务
     */
    @NotNull
    List<TenantCleanupTask> claimPendingTenantCleanupTasks(@Param("limit") int limit);

    /**
     * 按 Account 归属和乐观锁版本变更 Credential 状态。
     *
     * @param credentialId Credential 主键
     * @param accountId 所属 Account 主键
     * @param status 目标状态
     * @param version 当前乐观锁版本
     * @return 实际更新行数
     */
    int updateCredentialStatus(
            @Param("credentialId") @NotNull Long credentialId,
            @Param("accountId") @NotNull Long accountId,
            @Param("status") @NotNull CredentialStatus status,
            @Param("version") @NotNull Long version);

    /**
     * 按 Account 归属和乐观锁版本逻辑删除 Credential。
     *
     * @param credentialId Credential 主键
     * @param accountId 所属 Account 主键
     * @param version 当前乐观锁版本
     * @return 实际更新行数
     */
    int deleteCredential(
            @Param("credentialId") @NotNull Long credentialId,
            @Param("accountId") @NotNull Long accountId,
            @Param("version") @NotNull Long version);

    /** @param principal 已确定 Subject、业务范围和初始状态的 Principal 数据库事实 */
    void insertPrincipal(@Param("principal") @NotNull IamPrincipalEntity principal);

    /** @param membership 已确定 Subject、Tenant 和初始状态的 Membership 数据库事实 */
    void insertTenantMembership(@Param("membership") @NotNull IamTenantMembershipEntity membership);

    /**
     * 按 Membership、Tenant 和乐观锁版本更新成员状态。
     *
     * @param membershipId Membership 主键
     * @param tenantId Membership 所属 Tenant 主键
     * @param status 目标成员状态
     * @param version 当前 Membership 版本
     * @return 实际更新行数
     */
    int updateTenantMembershipStatus(
            @Param("membershipId") @NotNull Long membershipId,
            @Param("tenantId") @NotNull Long tenantId,
            @Param("status") @NotNull MembershipStatus status,
            @Param("version") @NotNull Long version);

    /**
     * 在 PLATFORM Principal 与版本均匹配时原子推进版本。
     *
     * @param principalId PLATFORM Principal 主键
     * @param version 调用方读取到的版本
     * @return 实际更新行数
     */
    int advancePlatformPrincipalVersion(@Param("principalId") @NotNull Long principalId, @Param("version") @NotNull Long version);

    /**
     * 查询指定租户当前关联的全部 Subject 主键。
     *
     * @param tenantId 租户主键
     * @return 去重后的 Subject 主键
     */
    @NotNull
    List<Long> selectTenantSubjectIds(@Param("tenantId") @NotNull Long tenantId);

    /**
     * 用一条 JOIN 查询装载多个 Subject 的完整会话状态行。
     *
     * @param subjectIds 需要登录或刷新的 Subject 主键
     * @return Subject、可选择 Principal、角色和权限的扁平结果
     */
    @NotNull
    List<SessionRow> selectSessionRows(@Param("accountIds") @NotNull List<Long> accountIds);

    /**
     * 按 Subject 与受支持类型批量读取 Credential 安全字段。
     *
     * @param subjectIds Subject 主键
     * @param credentialTypes 已注册 Scheme 支持的类型
     * @return 按 Subject、Account、Credential 排序的 JOIN 行
     */
    @NotNull
    List<CredentialRow> selectCredentialRowsBySubjectIds(
            @Param("subjectIds") @NotNull List<Long> subjectIds,
            @Param("credentialTypes") @NotNull Set<CredentialType> credentialTypes);

    /**
     * 批量读取审计资源引用需要的 Credential 与 Subject 关系。
     *
     * @param credentialIds Credential 主键
     * @return 不含摘要、盐或 Token 的 JOIN 行
     */
    @NotNull
    List<CredentialRow> selectCredentialReferenceRows(
            @Param("credentialIds") @NotNull Set<Long> credentialIds);

    /**
     * 按乐观锁版本更新 Principal 状态。
     *
     * @param principalId Principal 主键
     * @param status 目标状态
     * @param version 当前乐观锁版本
     * @return 实际更新行数
     */
    int updatePrincipalStatus(
            @Param("principalId") @NotNull Long principalId,
            @Param("status") @NotNull PrincipalStatus status,
            @Param("version") @NotNull Long version);

    /**
     * 按租户范围和乐观锁版本更新 TENANT Principal 状态。
     *
     * @param principalId TENANT Principal 主键
     * @param tenantId 所属租户主键
     * @param status 目标状态
     * @param version 当前乐观锁版本
     * @return 实际更新行数
     */
    int updateTenantPrincipalStatus(
            @Param("principalId") @NotNull Long principalId,
            @Param("tenantId") @NotNull Long tenantId,
            @Param("status") @NotNull PrincipalStatus status,
            @Param("version") @NotNull Long version);

    /**
     * 在 TENANT Principal 仍属于指定租户时原子推进版本。
     *
     * @param principalId TENANT Principal 主键
     * @param tenantId 所属租户主键
     * @param version 调用方读取到的版本
     * @return 实际更新行数
     */
    int advanceTenantPrincipalVersion(
            @Param("principalId") @NotNull Long principalId,
            @Param("tenantId") @NotNull Long tenantId,
            @Param("version") @NotNull Long version);

    /** @param tenant 已分配主键并确定初始状态的 Tenant 数据库事实 */
    void insertTenant(@Param("tenant") @NotNull IamTenantEntity tenant);

    /**
     * 登记一个租户编码的永久占用数据。
     *
     * @param tenantId 首次占用该编码的 Tenant 主键
     * @param code 已校验的租户编码
     */
    void insertTenantCode(
            @Param("tenantId") @NotNull Long tenantId, @Param("code") @NotNull String code);

    /**
     * 按当前资料持久化契约和乐观锁版本更新 Tenant，并返回更新前编码。
     *
     * @param tenantId Tenant 主键
     * @param code 当前请求提供的新编码
     * @param name 当前资料契约提供的新名称
     * @param version 当前乐观锁版本
     * @return 更新前编码；目标不存在或版本变化时返回 null
     */
    @Nullable
    String updateTenant(
            @Param("tenantId") @NotNull Long tenantId,
            @Param("code") @NotNull String code,
            @Param("name") @NotNull String name,
            @Param("version") @NotNull Long version);

    /**
     * 按乐观锁版本更新 Tenant 状态。
     *
     * @param tenantId Tenant 主键
     * @param status 目标状态
     * @param version 当前乐观锁版本
     * @return 实际更新行数
     */
    int updateTenantStatus(
            @Param("tenantId") @NotNull Long tenantId,
            @Param("status") @NotNull TenantStatus status,
            @Param("version") @NotNull Long version);

    /**
     * 锁定一个仍存在的租户，串行化租户归属资源写入与终态删除。
     *
     * @param tenantId 租户主键
     * @return 当前租户状态；租户不存在或已删除时为 {@code null}
     */
    @Nullable
    TenantStatus selectTenantStatusForShare(@Param("tenantId") @NotNull Long tenantId);

    /**
     * 将当前版本租户推进到不可恢复删除终态。
     *
     * @param tenantId 租户主键
     * @param version 当前乐观锁版本
     * @return 实际更新行数
     */
    int isolateTenant(
            @Param("tenantId") @NotNull Long tenantId,
            @Param("version") @NotNull Long version);

    /** @param tenantId 待隔离的租户主键 @return 实际隔离 Principal 数量 */
    int isolateTenantPrincipals(@Param("tenantId") @NotNull Long tenantId);

    /** @param tenantId 待隔离的租户主键 @return 实际隔离 Membership 数量 */
    int isolateTenantMemberships(@Param("tenantId") @NotNull Long tenantId);

    /** @param tenantId 待隔离的租户主键 @return 实际隔离角色数量 */
    int isolateTenantRoles(@Param("tenantId") @NotNull Long tenantId);

    /**
     * 幂等登记 Tenant 清理任务。
     *
     * @param taskId 清理任务主键
     * @param tenantId 已隔离租户主键
     */
    void insertTenantCleanupTask(
            @Param("taskId") @NotNull Long taskId,
            @Param("tenantId") @NotNull Long tenantId);

    /** @param tenantId 已隔离租户主键 @return 仍保留的已删除 TENANT Principal 主键 */
    @NotNull
    List<Long> selectIsolatedTenantPrincipalIds(@Param("tenantId") @NotNull Long tenantId);

    /** @param tenantId 已隔离租户主键 */
    void deleteIsolatedTenantRelations(@Param("tenantId") @NotNull Long tenantId);

    /**
     * 查询已隔离 Subject 仍保留的 Principal 主键。
     *
     * @param subjectId 已隔离 Subject 主键
     * @return 按主键固定排序的已删除 Principal 主键
     */
    @NotNull
    List<Long> selectIsolatedSubjectPrincipalIds(@Param("subjectId") @NotNull Long subjectId);

    /**
     * 查询一个 Subject 当前可见的租户成员关系。
     *
     * @param subjectId Subject 主键
     * @return 包含租户和成员状态的聚合结果
     */
    @NotNull
    List<TenantMembership> selectTenantMemberships(@Param("subjectId") @NotNull Long subjectId);

    /**
     * 删除 Principal 当前全部角色关系。
     *
     * @param principalId Principal 主键
     * @return 实际删除关系数
     */
    int deletePrincipalRoles(@Param("principalId") @NotNull Long principalId);

    /**
     * 批量写入 Principal 与 Role 关系。
     *
     * @param relations 已分配关系主键的 Principal 与 Role 关系
     * @return 实际写入行数
     */
    int insertPrincipalRoles(@Param("relations") @NotNull List<IamPrincipalRoleEntity> relations);

    /**
     * 查询绑定指定角色的全部未删除 Principal。
     *
     * @param roleId 角色主键
     * @return 当前仍绑定该角色的 Principal
     */
    @NotNull
    List<IamPrincipalEntity> selectPrincipalsByRole(@Param("roleId") @NotNull Long roleId);

    /**
     * 批量查询 PLATFORM Principal 当前绑定的有效平台角色。
     *
     * @param principalIds PLATFORM Principal 主键集合
     * @return 按 Principal 和角色编码排序的角色绑定行
     */
    @NotNull
    List<PlatformRole> selectPlatformPrincipalRoles(
            @Param("principalIds") @NotNull List<Long> principalIds);

    /**
     * 按固定编码批量查询权限，包含已经逻辑删除的记录。
     *
     * @param codes 权限固定编码
     * @return 每个编码最近使用的权限记录
     */
    @NotNull
    List<IamPermissionEntity> selectPermissionsIncludingDeleted(@Param("codes") @NotNull List<String> codes);

    /**
     * 批量新增或恢复权限目录记录。
     *
     * @param permissions 已分配主键并解析父节点的完整权限目录
     * @return 实际写入或更新行数
     */
    int upsertPermissions(@Param("permissions") @NotNull List<IamPermissionEntity> permissions);

    /**
     * 逻辑删除不在当前代码目录中的权限。
     *
     * @param codes 当前代码目录的完整权限编码集合
     * @return 实际更新行数
     */
    int deletePermissionsExcept(@Param("codes") @NotNull List<String> codes);

    /** @param role 已确定所属范围和初始状态的 Role 数据库事实 */
    void insertRole(@Param("role") @NotNull IamRoleEntity role);

    /**
     * 在精确平台或租户范围内按版本更新角色名称和状态。
     *
     * @param roleId 角色主键
     * @param tenantId 精确租户范围；平台角色为 null
     * @param name 新角色名称
     * @param status 新角色状态
     * @param version 调用方读取到的乐观锁版本
     * @return 实际更新行数
     */
    int updateRole(
            @Param("roleId") @NotNull Long roleId,
            @Param("tenantId") @Nullable Long tenantId,
            @Param("name") @NotNull String name,
            @Param("status") @NotNull RoleStatus status,
            @Param("version") @NotNull Long version);

    /**
     * 在精确范围和版本匹配时逻辑删除一个非内置角色。
     *
     * @param roleId 角色主键
     * @param tenantId 精确租户范围；平台角色为 null
     * @param version 调用方读取到的乐观锁版本
     * @return 实际更新行数
     */
    int deleteCustomRole(
            @Param("roleId") @NotNull Long roleId,
            @Param("tenantId") @Nullable Long tenantId,
            @Param("version") @NotNull Long version);

    /**
     * 统计当前仍绑定指定角色的 Principal 数量。
     *
     * @param roleId 角色主键
     * @return 未删除 Principal 的去重数量
     */
    long countPrincipalsByRole(@Param("roleId") @NotNull Long roleId);

    /**
     * 一次聚合一页角色的当前 Principal 使用量。
     *
     * @param roleIds 当前角色页去重主键
     * @return 每个输入角色的 Principal 和权限关系计数
     */
    @NotNull
    List<RoleUsageCount> selectRoleUsageCounts(@Param("roleIds") @NotNull List<Long> roleIds);

    /**
     * 删除角色当前全部权限关系。
     *
     * @param roleId 角色主键
     * @return 实际删除关系数
     */
    int deleteRolePermissions(@Param("roleId") @NotNull Long roleId);

    /**
     * 批量写入 Role 与 Permission 关系。
     *
     * @param relations 已分配关系主键的 Role 与 Permission 关系
     * @return 实际写入行数
     */
    int insertRolePermissions(@Param("relations") @NotNull List<IamRolePermissionEntity> relations);
}
