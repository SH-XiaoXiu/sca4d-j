package com.sca4d.tenant;

import com.sca4d.common.web.PageData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 添加和查询租户成员，并修改成员状态与租户角色。 */
public interface TenantMemberService {

    /**
     * 分页查询一个租户的成员及其角色分配。
     *
     * @param tenantId 租户主键
     * @param search 已转换的关键词；缺失时不按关键词过滤
     * @param status 目标成员状态；缺失时不按状态过滤
     * @param page 页码，从 1 开始
     * @param size 每页记录数
     * @return 成员及角色的分页结果
     */
    @NotNull
    PageData<TenantMember> page(
            @NotNull Long tenantId,
            @Nullable String search,
            @Nullable MembershipStatus status,
            long page,
            long size);

    /**
     * 读取一个属于指定租户的 TENANT Principal。
     *
     * @param tenantId 租户主键
     * @param principalId 成员 TENANT Principal 主键
     * @return 已聚合角色分配的成员
     * @throws com.sca4d.common.exception.BizException 成员不存在时抛出
     */
    @NotNull
    TenantMember require(@NotNull Long tenantId, @NotNull Long principalId);

    /**
     * 列出当前租户可以分配给成员的有效角色。
     *
     * @param tenantId 租户主键
     * @return 管理端角色选择项
     */
    @NotNull
    List<TenantRole> assignableRoles(@NotNull Long tenantId);

    /**
     * 创建租户 Account 后将其所属 Subject 加入租户并绑定角色。
     *
     * @param tenantId 目标租户主键
     * @param credential 新 Account 的认证材料
     * @param displayName 租户成员展示名称
     * @param phone 联系电话；缺失时不写入
     * @param email 联系邮箱；缺失时不写入
     * @param avatarFileId 头像文件主键；缺失时不绑定
     * @param avatarUploadGrant 文件签名阶段签发的头像消费授权；未绑定头像时不提供
     * @param actorPrincipalId 验证头像归属的操作 Principal
     * @param roleIds 初始角色主键集合
     * @return 新建 TENANT Principal 主键
     */
    <C> @NotNull Long createNewAccount(
            @NotNull Long tenantId,
            @NotNull String username,
            @NotNull C credential,
            @NotNull String displayName,
            @Nullable String phone,
            @Nullable String email,
            @Nullable Long avatarFileId,
            @Nullable String avatarUploadGrant,
            @NotNull Long actorPrincipalId,
            @NotNull List<Long> roleIds);

    /**
     * 使用已有 Account 所属 Subject 创建当前租户的成员 Principal。
     *
     * <p>Account 仅用于在成员工作流中明确选择既有认证身份；本方法不会为任意 Subject 建立通用关联，也不修改
     * Account 的认证材料。
     *
     * @param tenantId 目标租户主键
     * @param accountId 已有 Account 主键
     * @param displayName 租户内展示名称
     * @param roleIds 初始租户角色主键集合
     * @return 新建 TENANT Principal 主键
     */
    @NotNull
    Long addExistingAccount(
            @NotNull Long tenantId,
            @NotNull Long accountId,
            @NotNull String displayName,
            @NotNull List<Long> roleIds);

    /**
     * 修改成员 TENANT Principal 状态并清理该主体的会话授权缓存。
     *
     * <p>停用后 TENANT Principal 选择立即失败；同一 Subject 的 DEFAULT、PLATFORM 和其他租户主体状态不受影响。
     *
     * @param tenantId 租户主键
     * @param principalId 成员 TENANT Principal 主键
     * @param status 目标成员状态
     * @param version 调用方读取到的乐观锁版本
     */
    void changeStatus(
            @NotNull Long tenantId,
            @NotNull Long principalId,
            @NotNull MembershipStatus status,
            @NotNull Long version);

    /**
     * 替换成员的全部租户角色并失效会话权限缓存。
     *
     * @param tenantId 租户主键
     * @param principalId 成员 TENANT Principal 主键
     * @param roleIds 新角色主键集合
     * @param version 调用方读取到的成员 Principal 版本
     */
    void replaceRoles(
            @NotNull Long tenantId,
            @NotNull Long principalId,
            @NotNull List<Long> roleIds,
            @NotNull Long version);
}
