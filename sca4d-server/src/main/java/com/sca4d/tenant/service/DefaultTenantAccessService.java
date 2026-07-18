package com.sca4d.tenant.service;

import com.sca4d.common.access.AccessAction;
import com.sca4d.common.access.ResourceAccessDeniedException;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.resource.ResourceReference;
import com.sca4d.common.resource.ResourceReferenceKey;
import com.sca4d.common.resource.ResourceReferenceResolver;
import com.sca4d.common.web.ClientState;
import com.sca4d.entity.IamPrincipalEntity;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.principal.PrincipalService;
import com.sca4d.iam.repository.IamRepository;
import com.sca4d.tenant.TenantAccessService;
import com.sca4d.tenant.TenantClientStateTargets;
import com.sca4d.tenant.TenantErrorCode;
import com.sca4d.tenant.policy.PlatformTenantAccess;
import com.sca4d.tenant.policy.TenantAccessGrant;
import com.sca4d.tenant.policy.TenantAccessRule;
import com.sca4d.tenant.policy.TenantActionScope;
import com.sca4d.tenant.repository.TenantAccessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 租户业务模块 ACL 默认实现。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultTenantAccessService implements TenantAccessService {

    private final IamRepository iamRepository;
    private final PrincipalService principalService;
    private final TenantAccessRepository tenantAccessRepository;
    private final ResourceReferenceResolver resourceReferenceResolver;

    @Override
    public @NotNull TenantActionScope scope(
            @NotNull Long principalId, @NotNull AccessAction requiredAction) {
        AccessAction defaultAction = null;
        Map<Long, AccessAction> tenantActions = new LinkedHashMap<>();
        for (TenantAccessGrant grant : tenantAccessRepository.findGrants(principalId)) {
            if (grant.getTenantId() == null) {
                if (defaultAction != null) {
                    throw new ResourceAccessDeniedException();
                }
                defaultAction = grant.getAccessAction();
            } else if (tenantActions.putIfAbsent(grant.getTenantId(), grant.getAccessAction()) != null) {
                throw new ResourceAccessDeniedException();
            }
        }
        boolean allData = defaultAction != null && defaultAction.permits(requiredAction);
        Set<Long> included = new LinkedHashSet<>();
        Set<Long> excluded = new LinkedHashSet<>();
        for (Map.Entry<Long, AccessAction> entry : tenantActions.entrySet()) {
            if (allData && !entry.getValue().permits(requiredAction)) {
                excluded.add(entry.getKey());
            } else if (!allData && entry.getValue().permits(requiredAction)) {
                included.add(entry.getKey());
            }
        }
        return new TenantActionScope(allData, included, excluded);
    }

    @Override
    public @NotNull PlatformTenantAccess configuration(@NotNull Long targetPrincipalId) {
        IamPrincipalEntity principal = principalService.require(targetPrincipalId);
        if (principal.getPrincipalType() != PrincipalType.PLATFORM) {
            throw new BizException(IamErrorCode.PRINCIPAL_NOT_FOUND);
        }
        AccessAction defaultAction = null;
        Map<Long, AccessAction> tenantActions = new LinkedHashMap<>();
        for (TenantAccessGrant grant : tenantAccessRepository.findGrants(principal.getId())) {
            if (grant.getTenantId() == null) {
                if (defaultAction != null) {
                    throw new IllegalStateException("PLATFORM Principal 存在重复租户 ACL 默认规则");
                }
                defaultAction = grant.getAccessAction();
            } else if (tenantActions.putIfAbsent(grant.getTenantId(), grant.getAccessAction()) != null) {
                throw new IllegalStateException("PLATFORM Principal 存在重复租户 ACL 规则");
            }
        }
        List<ResourceReferenceKey> keys = tenantActions.keySet().stream()
                .sorted()
                .map(tenantId -> new ResourceReferenceKey("tenant", tenantId.toString()))
                .toList();
        Map<ResourceReferenceKey, ResourceReference> references = resourceReferenceResolver.resolve(keys);
        List<TenantAccessRule> rules = keys.stream()
                .filter(references::containsKey)
                .map(key -> new TenantAccessRule(
                        references.get(key), tenantActions.get(Long.valueOf(key.getResourceId()))))
                .toList();
        return new PlatformTenantAccess(principal.getId(), principal.getVersion(), defaultAction, rules);
    }

    @Transactional
    @Override
    public void replace(
            @NotNull Long actorPrincipalId,
            @NotNull Long targetPrincipalId,
            @Nullable AccessAction defaultAction,
            @NotNull List<TenantAccessGrant> grants,
            @NotNull Long version) {
        AccessAction actorDefaultAction = null;
        Map<Long, AccessAction> actorTenantActions = new LinkedHashMap<>();
        for (TenantAccessGrant actorGrant : tenantAccessRepository.findGrants(actorPrincipalId)) {
            if (actorGrant.getTenantId() == null) {
                if (actorDefaultAction != null) {
                    throw new ResourceAccessDeniedException();
                }
                actorDefaultAction = actorGrant.getAccessAction();
            } else if (actorTenantActions.putIfAbsent(actorGrant.getTenantId(), actorGrant.getAccessAction()) != null) {
                throw new ResourceAccessDeniedException();
            }
        }
        IamPrincipalEntity principal = principalService.require(targetPrincipalId);
        if (principal.getPrincipalType() != PrincipalType.PLATFORM) {
            throw new BizException(IamErrorCode.PRINCIPAL_NOT_FOUND);
        }
        Map<Long, AccessAction> distinct = new LinkedHashMap<>();
        for (TenantAccessGrant grant : grants) {
            Long tenantId = grant.getTenantId();
            if (tenantId == null || tenantId <= 0 || distinct.putIfAbsent(tenantId, grant.getAccessAction()) != null) {
                throw new BizException(TenantErrorCode.TENANT_ACCESS_INVALID_RULE);
            }
        }
        if (iamRepository.findExistingTenantIds(distinct.keySet()).size() != distinct.size()) {
            throw new BizException(TenantErrorCode.TENANT_NOT_FOUND);
        }
        if (defaultAction != null) {
            if (actorDefaultAction == null || !actorDefaultAction.permits(defaultAction)) {
                throw new ResourceAccessDeniedException();
            }
            for (Map.Entry<Long, AccessAction> actorEntry : actorTenantActions.entrySet()) {
                AccessAction requestedAction = distinct.getOrDefault(actorEntry.getKey(), defaultAction);
                if (!actorEntry.getValue().permits(requestedAction)) {
                    throw new ResourceAccessDeniedException();
                }
            }
        }
        for (Map.Entry<Long, AccessAction> entry : distinct.entrySet()) {
            AccessAction actorAction = actorTenantActions.getOrDefault(entry.getKey(), actorDefaultAction);
            if (actorAction == null || !actorAction.permits(entry.getValue())) {
                throw new ResourceAccessDeniedException();
            }
        }
        List<TenantAccessGrant> normalized = distinct.entrySet().stream()
                .filter(entry -> entry.getValue() != defaultAction)
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new TenantAccessGrant(entry.getKey(), entry.getValue()))
                .toList();
        iamRepository.advancePlatformPrincipalVersionForTenantAccess(principal.getId(), version);
        tenantAccessRepository.replace(principal.getId(), defaultAction, normalized);
        ClientState.refresh(TenantClientStateTargets.ACCESS);
        log.info(
                "平台身份租户访问规则已写入事务 | principalId={} version={} defaultAction={} ruleCount={}",
                principal.getId(),
                version + 1,
                defaultAction,
                normalized.size());
    }
}
