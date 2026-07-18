package com.sca4d.iam.principal.service;

import com.sca4d.authentication.AuthenticationClientStateTargets;
import com.sca4d.authentication.SessionService;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.web.ClientState;
import com.sca4d.common.web.PageData;
import com.sca4d.entity.IamPrincipalEntity;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.principal.PlatformPrincipal;
import com.sca4d.iam.principal.PlatformRole;
import com.sca4d.iam.principal.PrincipalService;
import com.sca4d.iam.principal.PrincipalStatus;
import com.sca4d.iam.repository.IamRepository;
import com.sca4d.role.RoleErrorCode;
import com.sca4d.tenant.MembershipStatus;
import com.sca4d.tenant.TenantErrorCode;
import com.sca4d.tenant.TenantRole;
import com.sca4d.tenant.TenantService;
import com.sca4d.tenant.repository.TenantAccessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 创建和选择 Principal，并修改 Principal 状态与角色关系。
 *
 * <p>一个 Subject 可以拥有不同范围的 Principal。Service 决定该范围能否创建或改变，IamRepository 负责
 * Principal 与角色关系的持久化一致性。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultPrincipalService implements PrincipalService {

    private final IamRepository iamRepository;
    private final SessionService sessionService;
    private final TenantService tenantService;
    private final TenantAccessRepository tenantAccessRepository;

    @Override
    public @NotNull IamPrincipalEntity createDefault(
            @NotNull Long subjectId, @NotNull String displayName) {
        // 默认 Principal 在 Subject 范围内唯一；Repository 负责并发唯一约束的精确异常翻译。
        if (iamRepository.findPrincipal(subjectId, PrincipalType.DEFAULT, null) != null) {
            throw new BizException(IamErrorCode.PRINCIPAL_EXISTS);
        }
        IamPrincipalEntity principal = iamRepository.createDefaultPrincipal(subjectId, displayName);
        sessionService.refreshAfterCommit(List.of(subjectId));
        return principal;
    }

    @Override
    @Transactional
    public @NotNull IamPrincipalEntity createPlatform(
            @NotNull Long subjectId, @NotNull String displayName) {
        // 平台 Principal 创建操作连续完成唯一性检查、主体写入、显式全租户 MANAGE 规则和 Session 刷新。
        if (iamRepository.findPrincipal(subjectId, PrincipalType.PLATFORM, null) != null) {
            throw new BizException(IamErrorCode.PRINCIPAL_EXISTS);
        }
        IamPrincipalEntity principal = iamRepository.createPrincipal(
                subjectId, PrincipalType.PLATFORM, null, displayName);
        tenantAccessRepository.grantAll(principal.getId());
        sessionService.refreshAfterCommit(List.of(subjectId));
        return principal;
    }

    @Override
    @Transactional
    public @NotNull IamPrincipalEntity createDelegatedPlatform(
            @NotNull Long subjectId, @NotNull String displayName) {
        // 委托平台主体必须从空角色、空租户 ACL 开始，后续授权由各自的正式资源接口显式完成。
        if (iamRepository.findPrincipal(subjectId, PrincipalType.PLATFORM, null) != null) {
            throw new BizException(IamErrorCode.PRINCIPAL_EXISTS);
        }
        IamPrincipalEntity principal = iamRepository.createPrincipal(
                subjectId, PrincipalType.PLATFORM, null, displayName);
        sessionService.refreshAfterCommit(List.of(subjectId));
        return principal;
    }

    @Transactional
    @Override
    public @NotNull IamPrincipalEntity createTenant(
            @NotNull Long subjectId,
            @NotNull Long tenantId,
            @NotNull String displayName,
            @NotNull List<Long> roleIds) {
        // 先持有租户共享行锁，再批量确认全部角色归属；终态删除与成员新增不能在不同节点交叉提交。
        tenantService.lockActiveForWrite(tenantId);
        List<Long> distinctRoleIds = List.copyOf(new LinkedHashSet<>(roleIds));
        if (!distinctRoleIds.isEmpty()) {
            if (iamRepository.findActiveTenantRoleIds(tenantId, distinctRoleIds).size() != distinctRoleIds.size()) {
                throw new BizException(RoleErrorCode.ROLE_NOT_IN_TENANT);
            }
        }
        if (iamRepository.findPrincipal(subjectId, PrincipalType.TENANT, tenantId) != null) {
            throw new BizException(IamErrorCode.PRINCIPAL_EXISTS);
        }
        IamPrincipalEntity principal = iamRepository.createTenantPrincipal(subjectId, tenantId, displayName);
        iamRepository.replacePrincipalRoles(principal.getId(), distinctRoleIds);
        sessionService.refreshAfterCommit(List.of(subjectId));
        return principal;
    }

    @Override
    public @NotNull IamPrincipalEntity require(@NotNull Long principalId) {
        // 删除或不存在的主体不能继续承担成员、角色或请求执行含义。
        IamPrincipalEntity principal = iamRepository.findPrincipalById(principalId);
        if (principal == null) {
            throw new BizException(IamErrorCode.PRINCIPAL_NOT_FOUND);
        }
        return principal;
    }

    @Override
    public @NotNull IamPrincipalEntity require(
            @NotNull Long subjectId,
            @NotNull PrincipalType principalType,
            @Nullable Long tenantId) {
        IamPrincipalEntity principal = iamRepository.findPrincipal(subjectId, principalType, tenantId);
        if (principal == null) {
            throw new BizException(IamErrorCode.PRINCIPAL_NOT_FOUND);
        }
        return principal;
    }

    @Override
    public boolean hasPlatformPrincipal() {
        return iamRepository.countPrincipals(PrincipalType.PLATFORM) > 0;
    }

    @Override
    public @NotNull Map<Long, PlatformPrincipal> findPlatformPrincipals(
            @NotNull List<Long> subjectIds) {
        return iamRepository.findPlatformPrincipalsBySubjectIds(subjectIds);
    }

    @Override
    public @NotNull PageData<PlatformPrincipal> pagePlatformPrincipals(long page, long size) {
        return iamRepository.pagePlatformPrincipals(page, size);
    }

    @Override
    public @NotNull PlatformPrincipal requirePlatformPrincipal(@NotNull Long principalId) {
        IamPrincipalEntity principal = require(principalId);
        if (principal.getPrincipalType() != PrincipalType.PLATFORM) {
            throw new BizException(IamErrorCode.PRINCIPAL_NOT_FOUND);
        }
        PlatformPrincipal platform = iamRepository.findPlatformPrincipalsBySubjectIds(List.of(principal.getSubjectId()))
                .get(principal.getSubjectId());
        if (platform == null || !principalId.equals(platform.getPrincipalId())) {
            throw new BizException(IamErrorCode.PRINCIPAL_NOT_FOUND);
        }
        return platform;
    }

    @Override
    public @NotNull List<PlatformRole> assignablePlatformRoles(@NotNull Long principalId) {
        IamPrincipalEntity principal = require(principalId);
        if (principal.getPrincipalType() != PrincipalType.PLATFORM) {
            throw new BizException(IamErrorCode.PRINCIPAL_NOT_FOUND);
        }
        return iamRepository.findAssignablePlatformRoles();
    }

    @Override
    public @NotNull List<TenantRole> assignableTenantRoles(@NotNull Long tenantId) {
        return iamRepository.findAssignableTenantRoles(tenantId);
    }

    @Override
    public void changeStatus(
            @NotNull Long principalId, @NotNull PrincipalStatus status, @NotNull Long version) {
        IamPrincipalEntity principal = require(principalId);
        iamRepository.updatePrincipalStatus(principalId, status, version);
        sessionService.refreshAfterCommit(List.of(principal.getSubjectId()));
        ClientState.refresh(AuthenticationClientStateTargets.ME);
        log.info(
                "身份状态更新完成 | principalId={} status={} version={} subjectId={}",
                principalId,
                status,
                version + 1,
                principal.getSubjectId());
    }

    @Override
    public void changePlatformStatus(
            @NotNull Long principalId,
            @NotNull PrincipalStatus status,
            @NotNull Long version) {
        IamPrincipalEntity principal = require(principalId);
        if (principal.getPrincipalType() != PrincipalType.PLATFORM) {
            throw new BizException(IamErrorCode.PRINCIPAL_NOT_FOUND);
        }
        iamRepository.updatePrincipalStatus(principalId, status, version);
        sessionService.refreshAfterCommit(List.of(principal.getSubjectId()));
        ClientState.refresh(AuthenticationClientStateTargets.ME);
        log.info(
                "平台身份状态更新完成 | principalId={} status={} version={} subjectId={}",
                principalId,
                status,
                version + 1,
                principal.getSubjectId());
    }

    @Override
    public void changeTenantStatus(
            @NotNull Long principalId,
            @NotNull Long tenantId,
            @NotNull MembershipStatus status,
            @NotNull Long version) {
        IamPrincipalEntity principal = require(principalId);
        if (principal.getPrincipalType() != PrincipalType.TENANT
                || !tenantId.equals(principal.getTenantId())
                || principal.getMembershipId() == null) {
            throw new BizException(TenantErrorCode.TENANT_MEMBER_NOT_FOUND);
        }
        iamRepository.updateTenantMembershipStatus(principal.getMembershipId(), tenantId, status, version);
        sessionService.refreshAfterCommit(List.of(principal.getSubjectId()));
        ClientState.refresh(AuthenticationClientStateTargets.ME);
    }

    @Transactional
    @Override
    public void replaceTenantRoles(
            @NotNull Long principalId,
            @NotNull Long tenantId,
            @NotNull List<Long> roleIds,
            @NotNull Long version) {
        // 先验证主体确属目标租户，再校验每个角色的租户归属，最后以单个关系替换操作持久化角色集合。
        IamPrincipalEntity principal = require(principalId);
        if (principal.getPrincipalType() != PrincipalType.TENANT || !tenantId.equals(principal.getTenantId())) {
            throw new BizException(TenantErrorCode.TENANT_MEMBER_NOT_FOUND);
        }
        List<Long> distinctRoleIds = List.copyOf(new LinkedHashSet<>(roleIds));
        if (!distinctRoleIds.isEmpty()) {
            if (iamRepository.findActiveTenantRoleIds(tenantId, distinctRoleIds).size() != distinctRoleIds.size()) {
                throw new BizException(RoleErrorCode.ROLE_NOT_IN_TENANT);
            }
        }
        iamRepository.replaceTenantPrincipalRoles(principalId, tenantId, distinctRoleIds, version);
        sessionService.refreshAfterCommit(List.of(principal.getSubjectId()));
        ClientState.refresh(AuthenticationClientStateTargets.ME);
    }

    @Transactional
    @Override
    public void replacePlatformRoles(
            @NotNull Long principalId, @NotNull List<Long> roleIds, @NotNull Long version) {
        IamPrincipalEntity principal = require(principalId);
        if (principal.getPrincipalType() != PrincipalType.PLATFORM) {
            throw new BizException(IamErrorCode.PRINCIPAL_NOT_FOUND);
        }
        List<Long> distinctRoleIds = List.copyOf(new LinkedHashSet<>(roleIds));
        if (!distinctRoleIds.isEmpty()
                && iamRepository.findActivePlatformRoleIds(distinctRoleIds).size() != distinctRoleIds.size()) {
            throw new BizException(RoleErrorCode.ROLE_NOT_PLATFORM);
        }
        iamRepository.replacePlatformPrincipalRoles(principalId, distinctRoleIds, version);
        sessionService.refreshAfterCommit(List.of(principal.getSubjectId()));
        ClientState.refresh(AuthenticationClientStateTargets.ME);
        log.info(
                "平台身份角色更新已写入事务，会话刷新已登记 | principalId={} version={} roleCount={} subjectId={}",
                principalId,
                version + 1,
                distinctRoleIds.size(),
                principal.getSubjectId());
    }

    @Override
    public void refreshRoleSessions(@NotNull Long roleId) {
        List<Long> subjectIds = iamRepository.findSubjectIdsAssignedToRole(roleId);
        sessionService.refreshAfterCommit(subjectIds);
    }

    @Override
    public void refreshTenantSessions(@NotNull Long tenantId) {
        sessionService.refreshAfterCommit(iamRepository.findTenantSubjectIds(tenantId));
    }
}
