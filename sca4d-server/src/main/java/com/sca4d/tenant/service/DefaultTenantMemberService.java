package com.sca4d.tenant.service;

import com.sca4d.common.access.AccessAction;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.web.PageData;
import com.sca4d.entity.IamPrincipalEntity;
import com.sca4d.iam.account.AccountProvisioningService;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.account.ProvisionedAccount;
import com.sca4d.iam.principal.PrincipalService;
import com.sca4d.tenant.MembershipStatus;
import com.sca4d.tenant.TenantErrorCode;
import com.sca4d.tenant.TenantMember;
import com.sca4d.tenant.TenantMemberService;
import com.sca4d.tenant.TenantRole;
import com.sca4d.tenant.TenantService;
import com.sca4d.tenant.policy.TenantMemberAccessPolicy;
import com.sca4d.tenant.repository.TenantMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/** 添加、更新和删除租户成员，并修改成员的租户角色。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultTenantMemberService implements TenantMemberService {

    private final TenantService tenantService;
    private final AccountProvisioningService accountProvisioningService;
    private final AccountService accountService;
    private final PrincipalService principalService;
    private final TenantMemberAccessPolicy tenantMemberAccessPolicy;
    private final TenantMemberRepository tenantMemberRepository;

    @Override
    public @NotNull PageData<TenantMember> page(
            @NotNull Long tenantId,
            @Nullable String search,
            @Nullable MembershipStatus status,
            long page,
            long size) {
        // 先确认租户存在，再加载成员聚合，防止空页结果掩盖错误租户主键。
        tenantService.require(tenantId);
        tenantMemberAccessPolicy.require(tenantId, AccessAction.READ);
        String normalizedSearch = StringUtils.hasText(search) ? search.trim() : null;
        return tenantMemberRepository.page(
                tenantId, normalizedSearch, status, page, size);
    }

    @Override
    public @NotNull TenantMember require(@NotNull Long tenantId, @NotNull Long principalId) {
        // 成员读取同时校验 Principal 的租户归属，调用方不能仅凭 Principal 主键跨租户访问。
        tenantMemberAccessPolicy.require(tenantId, AccessAction.READ);
        TenantMember member = tenantMemberRepository.find(tenantId, principalId);
        if (member == null) {
            throw new BizException(TenantErrorCode.TENANT_MEMBER_NOT_FOUND);
        }
        return member;
    }

    @Override
    public @NotNull List<TenantRole> assignableRoles(@NotNull Long tenantId) {
        tenantService.require(tenantId);
        tenantMemberAccessPolicy.require(tenantId, AccessAction.READ);
        return principalService.assignableTenantRoles(tenantId);
    }

    @Transactional
    @Override
    public <C> @NotNull Long createNewAccount(
            @NotNull Long tenantId,
            @NotNull String username,
            @NotNull C credential,
            @NotNull String displayName,
            @Nullable String phone,
            @Nullable String email,
            @Nullable Long avatarFileId,
            @Nullable String avatarUploadGrant,
            @NotNull Long actorPrincipalId,
            @NotNull List<Long> roleIds) {
        tenantMemberAccessPolicy.require(tenantId, AccessAction.WRITE);
        // 新 Account、DEFAULT Principal、默认资料、TENANT Principal 与初始角色必须作为一个成员加入事务提交。
        tenantService.requireActive(tenantId);
        ProvisionedAccount account = accountProvisioningService.create(
                username,
                credential,
                AccountScopeType.TENANT,
                tenantId,
                displayName,
                phone,
                email,
                avatarFileId,
                avatarUploadGrant,
                actorPrincipalId);
        IamPrincipalEntity principal = principalService.createTenant(
                account.getSubject().getId(), tenantId, displayName, roleIds);
        log.info(
                "租户成员创建已写入事务 | tenantId={} principalId={} subjectId={} roleCount={}",
                tenantId,
                principal.getId(),
                account.getSubject().getId(),
                roleIds.size());
        return principal.getId();
    }

    @Transactional
    @Override
    public @NotNull Long addExistingAccount(
            @NotNull Long tenantId,
            @NotNull Long accountId,
            @NotNull String displayName,
            @NotNull List<Long> roleIds) {
        tenantMemberAccessPolicy.require(tenantId, AccessAction.WRITE);
        tenantService.requireActive(tenantId);
        // Account 只是成员工作流中选择既有 Subject 的凭据入口，成员关系仍通过 TENANT Principal 单独创建。
        IamPrincipalEntity principal = principalService.createTenant(
                accountService.require(accountId).getSubjectId(), tenantId, displayName, roleIds);
        log.info(
                "既有账号已加入租户成员 | tenantId={} accountId={} principalId={} roleCount={}",
                tenantId,
                accountId,
                principal.getId(),
                roleIds.size());
        return principal.getId();
    }

    @Override
    public void changeStatus(
            @NotNull Long tenantId,
            @NotNull Long principalId,
            @NotNull MembershipStatus status,
            @NotNull Long version) {
        tenantMemberAccessPolicy.require(tenantId, AccessAction.WRITE);
        // 成员状态变化只影响指定 TENANT Principal；PrincipalService 共用清理该主体的会话权限缓存。
        require(tenantId, principalId);
        principalService.changeTenantStatus(principalId, tenantId, status, version);
        log.info(
                "租户成员状态更新完成 | tenantId={} principalId={} status={} version={}",
                tenantId,
                principalId,
                status,
                version + 1);
    }

    @Override
    public void replaceRoles(
            @NotNull Long tenantId,
            @NotNull Long principalId,
            @NotNull List<Long> roleIds,
            @NotNull Long version) {
        tenantMemberAccessPolicy.require(tenantId, AccessAction.MANAGE);
        // 角色集合持久化成功后由 PrincipalService 失效指定 Principal 的 SessionPermission。
        require(tenantId, principalId);
        principalService.replaceTenantRoles(principalId, tenantId, roleIds, version);
        log.info(
                "租户成员角色更新完成 | tenantId={} principalId={} version={} roleCount={}",
                tenantId,
                principalId,
                version + 1,
                roleIds.size());
    }
}
