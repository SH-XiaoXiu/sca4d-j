package com.sca4d.iam.resource;

import com.sca4d.common.access.AccessAction;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.common.resource.ResourceNavigationState;
import com.sca4d.common.resource.ResourceReference;
import com.sca4d.common.resource.ResourceReferenceContext;
import com.sca4d.common.resource.ResourceReferenceContributor;
import com.sca4d.common.resource.ResourceReferenceKey;
import com.sca4d.iam.repository.IamRepository;
import com.sca4d.platform.PlatformPermissions;
import com.sca4d.tenant.TenantPermissions;
import com.sca4d.tenant.policy.TenantAccessGrant;
import com.sca4d.tenant.policy.TenantActionScope;
import com.sca4d.tenant.repository.TenantAccessRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** IAM Subject、租户、成员、登录凭证和角色的批量资源引用贡献者。 */
@Component
@RequiredArgsConstructor
public class IamResourceReferenceContributor implements ResourceReferenceContributor {

    private static final Set<String> RESOURCE_TYPES = Set.of(
            IamResourceTypes.SUBJECT,
            IamResourceTypes.ACCOUNT,
            IamResourceTypes.TENANT,
            IamResourceTypes.TENANT_MEMBER,
            IamResourceTypes.PRINCIPAL,
            IamResourceTypes.CREDENTIAL,
            IamResourceTypes.ROLE);

    private final IamRepository repository;
    private final TenantAccessRepository tenantAccessRepository;
    private final LocalizedMessages messages;

    @Override
    public @NotNull Set<String> resourceTypes() {
        return RESOURCE_TYPES;
    }

    @Override
    public @NotNull Map<ResourceReferenceKey, ResourceReference> resolve(
            @NotNull Set<ResourceReferenceKey> keys, @NotNull ResourceReferenceContext context) {
        // 资源引用在平台工作区可能携带多个租户归属对象；一次请求只读取一次范围，后续逐项仅做内存判断。
        TenantActionScope tenantScope = null;
        if (context.getPrincipalType() == PrincipalType.PLATFORM) {
            AccessAction defaultAction = null;
            Set<Long> included = new LinkedHashSet<>();
            Set<Long> excluded = new LinkedHashSet<>();
            Map<Long, AccessAction> exactActions = new LinkedHashMap<>();
            for (TenantAccessGrant grant : tenantAccessRepository.findGrants(context.getPrincipalId())) {
                if (grant.getTenantId() == null) {
                    defaultAction = grant.getAccessAction();
                } else {
                    exactActions.put(grant.getTenantId(), grant.getAccessAction());
                }
            }
            boolean allData = defaultAction != null && defaultAction.permits(AccessAction.READ);
            for (Map.Entry<Long, AccessAction> entry : exactActions.entrySet()) {
                if (allData && !entry.getValue().permits(AccessAction.READ)) {
                    excluded.add(entry.getKey());
                } else if (!allData && entry.getValue().permits(AccessAction.READ)) {
                    included.add(entry.getKey());
                }
            }
            tenantScope = new TenantActionScope(allData, included, excluded);
        }

        Set<ResourceReferenceKey> subjectKeys = new LinkedHashSet<>();
        Set<ResourceReferenceKey> accountKeys = new LinkedHashSet<>();
        Set<ResourceReferenceKey> tenantKeys = new LinkedHashSet<>();
        Set<ResourceReferenceKey> memberKeys = new LinkedHashSet<>();
        Set<ResourceReferenceKey> principalKeys = new LinkedHashSet<>();
        Set<ResourceReferenceKey> credentialKeys = new LinkedHashSet<>();
        Set<ResourceReferenceKey> roleKeys = new LinkedHashSet<>();
        Set<Long> subjectIds = new LinkedHashSet<>();
        Set<Long> accountIds = new LinkedHashSet<>();
        Set<Long> tenantIds = new LinkedHashSet<>();
        Set<Long> principalIds = new LinkedHashSet<>();
        Set<Long> credentialIds = new LinkedHashSet<>();
        Set<Long> roleIds = new LinkedHashSet<>();
        for (ResourceReferenceKey key : keys) {
            Set<ResourceReferenceKey> typedKeys;
            Set<Long> typedIds;
            switch (key.getResourceType()) {
                case IamResourceTypes.ACCOUNT -> {
                    typedKeys = accountKeys;
                    typedIds = accountIds;
                }
                case IamResourceTypes.SUBJECT -> {
                    typedKeys = subjectKeys;
                    typedIds = subjectIds;
                }
                case IamResourceTypes.TENANT -> {
                    typedKeys = tenantKeys;
                    typedIds = tenantIds;
                }
                case IamResourceTypes.TENANT_MEMBER -> {
                    typedKeys = memberKeys;
                    typedIds = principalIds;
                }
                case IamResourceTypes.PRINCIPAL -> {
                    typedKeys = principalKeys;
                    typedIds = principalIds;
                }
                case IamResourceTypes.CREDENTIAL -> {
                    typedKeys = credentialKeys;
                    typedIds = credentialIds;
                }
                case IamResourceTypes.ROLE -> {
                    typedKeys = roleKeys;
                    typedIds = roleIds;
                }
                default -> {
                    continue;
                }
            }
            typedKeys.add(key);
            try {
                typedIds.add(Long.valueOf(key.getResourceId()));
            } catch (NumberFormatException ignored) {
                // 非数字业务主键仍保留在结果中，并在对应分支返回删除态。
            }
        }

        Map<Long, SubjectReference> subjects = new LinkedHashMap<>();
        for (SubjectReference subject : repository.findSubjectReferences(Set.copyOf(subjectIds))) {
            subjects.put(subject.getSubjectId(), subject);
        }
        Map<Long, AccountReference> accounts = new LinkedHashMap<>();
        for (AccountReference account : repository.findAccountReferences(Set.copyOf(accountIds))) {
            accounts.put(account.getAccountId(), account);
        }
        Map<Long, TenantReference> tenants = new LinkedHashMap<>();
        for (TenantReference tenant : repository.findTenantReferences(Set.copyOf(tenantIds))) {
            tenants.put(tenant.getTenantId(), tenant);
        }
        Map<Long, PrincipalReference> principals = new LinkedHashMap<>();
        for (PrincipalReference principal : repository.findPrincipalReferences(Set.copyOf(principalIds))) {
            principals.put(principal.getPrincipalId(), principal);
        }
        Map<Long, CredentialReference> credentials = new LinkedHashMap<>();
        for (CredentialReference credential : repository.findCredentialReferences(Set.copyOf(credentialIds))) {
            credentials.put(credential.getCredentialId(), credential);
        }
        Map<Long, RoleReference> roles = new LinkedHashMap<>();
        for (RoleReference role : repository.findRoleReferences(Set.copyOf(roleIds))) {
            roles.put(role.getRoleId(), role);
        }

        String deletedReason = messages.get("resource.reference.deleted");
        String forbiddenReason = messages.get("resource.reference.forbidden");
        Map<ResourceReferenceKey, ResourceReference> references = new LinkedHashMap<>();

        for (ResourceReferenceKey key : accountKeys) {
            AccountReference account;
            try {
                account = accounts.get(Long.valueOf(key.getResourceId()));
            } catch (NumberFormatException ignored) {
                account = null;
            }
            if (account == null || account.isDeleted() || account.getScopeType() == null) {
                references.put(key, new ResourceReference(
                        key, null, null, ResourceNavigationState.DELETED, null, deletedReason));
                continue;
            }
            String label = account.getDisplayName() == null || account.getDisplayName().isBlank()
                    ? messages.get("resource.reference.account")
                    : account.getDisplayName();
            boolean global = account.getScopeType() == com.sca4d.common.auth.AccountScopeType.GLOBAL;
            boolean platform = context.getPrincipalType() == PrincipalType.PLATFORM
                    && (global
                            ? context.hasPermission(PlatformPermissions.ACCOUNTS_READ)
                            : context.hasPermission(PlatformPermissions.TENANT_MEMBERS_READ)
                                    && account.getScopeId() != null
                                    && tenantScope != null
                                    && (tenantScope.isAllData()
                                            ? !tenantScope.getExcludedTenantIds().contains(account.getScopeId())
                                            : tenantScope.getTenantIds().contains(account.getScopeId())));
            boolean tenant = !global
                    && context.getPrincipalType() == PrincipalType.TENANT
                    && account.getScopeId() != null
                    && account.getScopeId().equals(context.getTenantId())
                    && context.hasPermission(TenantPermissions.TENANT_MEMBERS_READ);
            String path = global
                    ? "/platform/accounts/" + key.getResourceId()
                    : platform
                            ? "/platform/tenants/" + account.getScopeId() + "/accounts/" + key.getResourceId()
                            : "/tenant/tenants/" + account.getScopeId() + "/accounts/" + key.getResourceId();
            references.put(key, platform || tenant
                    ? new ResourceReference(
                            key, label, account.getScopeId(), ResourceNavigationState.NAVIGABLE, path, null)
                    : new ResourceReference(
                            key, null, account.getScopeId(), ResourceNavigationState.FORBIDDEN, null, forbiddenReason));
        }

        for (ResourceReferenceKey key : subjectKeys) {
            SubjectReference subject;
            try {
                subject = subjects.get(Long.valueOf(key.getResourceId()));
            } catch (NumberFormatException ignored) {
                subject = null;
            }
            if (subject == null || subject.isDeleted()) {
                references.put(key, new ResourceReference(
                        key, null, null, ResourceNavigationState.DELETED, null, deletedReason));
                continue;
            }
            String label = subject.getDisplayName() == null || subject.getDisplayName().isBlank()
                    ? messages.get("resource.reference.subject")
                    : subject.getDisplayName();
            references.put(key, new ResourceReference(
                    key, label, null, ResourceNavigationState.FORBIDDEN, null, forbiddenReason));
        }

        for (ResourceReferenceKey key : tenantKeys) {
            TenantReference tenant;
            try {
                tenant = tenants.get(Long.valueOf(key.getResourceId()));
            } catch (NumberFormatException ignored) {
                tenant = null;
            }
            if (tenant == null || tenant.isDeleted()) {
                references.put(key, new ResourceReference(
                        key, null, null, ResourceNavigationState.DELETED, null, deletedReason));
                continue;
            }
            boolean inScope = context.getPrincipalType() != PrincipalType.PLATFORM
                    || (tenantScope != null
                            && (tenantScope.isAllData()
                                    ? !tenantScope.getExcludedTenantIds().contains(tenant.getTenantId())
                                    : tenantScope.getTenantIds().contains(tenant.getTenantId())));
            boolean allowed = context.getPrincipalType() == PrincipalType.PLATFORM
                    && context.hasPermission(PlatformPermissions.TENANTS_READ)
                    && inScope;
            String label = tenant.getName() == null || tenant.getName().isBlank()
                    ? messages.get("resource.reference.tenant")
                    : tenant.getName();
            references.put(key, allowed
                    ? new ResourceReference(
                            key,
                            label,
                            tenant.getTenantId(),
                            ResourceNavigationState.NAVIGABLE,
                            "/platform/tenants/" + key.getResourceId(),
                            null)
                    : new ResourceReference(
                            key,
                            null,
                            tenant.getTenantId(),
                            ResourceNavigationState.FORBIDDEN,
                            null,
                            forbiddenReason));
        }

        for (ResourceReferenceKey key : memberKeys) {
            PrincipalReference principal;
            try {
                principal = principals.get(Long.valueOf(key.getResourceId()));
            } catch (NumberFormatException ignored) {
                principal = null;
            }
            if (principal == null || principal.isDeleted() || principal.getTenantId() == null) {
                references.put(key, new ResourceReference(
                        key, null, null, ResourceNavigationState.DELETED, null, deletedReason));
                continue;
            }
            boolean inScope = context.getPrincipalType() != PrincipalType.PLATFORM
                    || (tenantScope != null
                            && (tenantScope.isAllData()
                                    ? !tenantScope.getExcludedTenantIds().contains(principal.getTenantId())
                                    : tenantScope.getTenantIds().contains(principal.getTenantId())));
            boolean platform = context.getPrincipalType() == PrincipalType.PLATFORM
                    && context.hasPermission(PlatformPermissions.TENANT_MEMBERS_READ)
                    && inScope;
            boolean tenant = context.getPrincipalType() == PrincipalType.TENANT
                    && principal.getTenantId().equals(context.getTenantId())
                    && context.hasPermission(TenantPermissions.TENANT_MEMBERS_READ);
            String label = principal.getDisplayName() == null || principal.getDisplayName().isBlank()
                    ? messages.get("resource.reference.member")
                    : principal.getDisplayName();
            String path = platform
                    ? "/platform/tenants/" + principal.getTenantId() + "/members/" + key.getResourceId()
                    : "/tenant/tenants/" + principal.getTenantId() + "/members/" + key.getResourceId();
            references.put(key, platform || tenant
                    ? new ResourceReference(
                            key,
                            label,
                            principal.getTenantId(),
                            ResourceNavigationState.NAVIGABLE,
                            path,
                            null)
                    : new ResourceReference(
                            key,
                            null,
                            principal.getTenantId(),
                            ResourceNavigationState.FORBIDDEN,
                            null,
                            forbiddenReason));
        }

        for (ResourceReferenceKey key : principalKeys) {
            PrincipalReference principal;
            try {
                principal = principals.get(Long.valueOf(key.getResourceId()));
            } catch (NumberFormatException ignored) {
                principal = null;
            }
            if (principal == null || principal.isDeleted() || principal.getPrincipalType() == null) {
                references.put(key, new ResourceReference(
                        key, null, null, ResourceNavigationState.DELETED, null, deletedReason));
                continue;
            }
            String label = principal.getDisplayName() == null || principal.getDisplayName().isBlank()
                    ? messages.get("resource.reference.principal")
                    : principal.getDisplayName();
            if (principal.getPrincipalType() == PrincipalType.PLATFORM) {
                boolean allowed = context.getPrincipalType() == PrincipalType.PLATFORM
                        && context.hasPermission(PlatformPermissions.OPERATORS_READ);
                references.put(key, allowed
                        ? new ResourceReference(
                                key,
                                label,
                                null,
                                ResourceNavigationState.NAVIGABLE,
                                "/platform/operators/" + key.getResourceId(),
                                null)
                        : new ResourceReference(
                                key, null, null, ResourceNavigationState.FORBIDDEN, null, forbiddenReason));
                continue;
            }
            if (principal.getPrincipalType() != PrincipalType.TENANT) {
                references.put(key, new ResourceReference(
                        key, null, null, ResourceNavigationState.FORBIDDEN, null, forbiddenReason));
                continue;
            }
            if (principal.getTenantId() == null) {
                references.put(key, new ResourceReference(
                        key, null, null, ResourceNavigationState.DELETED, null, deletedReason));
                continue;
            }
            boolean inScope = context.getPrincipalType() != PrincipalType.PLATFORM
                    || (tenantScope != null
                            && (tenantScope.isAllData()
                                    ? !tenantScope.getExcludedTenantIds().contains(principal.getTenantId())
                                    : tenantScope.getTenantIds().contains(principal.getTenantId())));
            boolean platform = context.getPrincipalType() == PrincipalType.PLATFORM
                    && context.hasPermission(PlatformPermissions.TENANT_MEMBERS_READ)
                    && inScope;
            boolean tenant = context.getPrincipalType() == PrincipalType.TENANT
                    && principal.getTenantId().equals(context.getTenantId())
                    && context.hasPermission(TenantPermissions.TENANT_MEMBERS_READ);
            String path = platform
                    ? "/platform/tenants/" + principal.getTenantId() + "/members/" + key.getResourceId()
                    : "/tenant/tenants/" + principal.getTenantId() + "/members/" + key.getResourceId();
            references.put(key, platform || tenant
                    ? new ResourceReference(
                            key,
                            label,
                            principal.getTenantId(),
                            ResourceNavigationState.NAVIGABLE,
                            path,
                            null)
                    : new ResourceReference(
                            key,
                            null,
                            principal.getTenantId(),
                            ResourceNavigationState.FORBIDDEN,
                            null,
                            forbiddenReason));
        }

        for (ResourceReferenceKey key : credentialKeys) {
            CredentialReference credential;
            try {
                credential = credentials.get(Long.valueOf(key.getResourceId()));
            } catch (NumberFormatException ignored) {
                credential = null;
            }
            if (credential == null || credential.deleted()) {
                references.put(key, new ResourceReference(
                        key, null, null, ResourceNavigationState.DELETED, null, deletedReason));
                continue;
            }
            boolean allowed = context.getPrincipalType() == PrincipalType.PLATFORM
                    && context.hasPermission(PlatformPermissions.ACCOUNTS_READ);
            String identifier = credential.getIdentifier() == null || credential.getIdentifier().isBlank()
                    ? messages.get("resource.reference.credential-without-account")
                    : credential.getIdentifier();
            String label = messages.get("resource.reference.credential", identifier);
            references.put(key, allowed
                    ? new ResourceReference(
                            key,
                            label,
                            null,
                            ResourceNavigationState.NAVIGABLE,
                            "/platform/accounts/" + credential.getAccountId()
                                    + "/security?credentialId=" + key.getResourceId(),
                            null)
                    : new ResourceReference(
                            key, null, null, ResourceNavigationState.FORBIDDEN, null, forbiddenReason));
        }

        for (ResourceReferenceKey key : roleKeys) {
            RoleReference role;
            try {
                role = roles.get(Long.valueOf(key.getResourceId()));
            } catch (NumberFormatException ignored) {
                role = null;
            }
            if (role == null || role.isDeleted()) {
                references.put(key, new ResourceReference(
                        key, null, null, ResourceNavigationState.DELETED, null, deletedReason));
                continue;
            }
            String label = role.getName() == null || role.getName().isBlank()
                    ? messages.get("resource.reference.role")
                    : role.getName();
            if (role.getTenantId() == null) {
                boolean allowed = context.getPrincipalType() == PrincipalType.PLATFORM
                        && context.hasPermission(PlatformPermissions.ROLES_READ);
                references.put(key, allowed
                        ? new ResourceReference(
                                key,
                                label,
                                null,
                                ResourceNavigationState.NAVIGABLE,
                                "/platform/roles/" + key.getResourceId(),
                                null)
                        : new ResourceReference(
                                key, null, null, ResourceNavigationState.FORBIDDEN, null, forbiddenReason));
                continue;
            }
            boolean inScope = context.getPrincipalType() != PrincipalType.PLATFORM
                    || (tenantScope != null
                            && (tenantScope.isAllData()
                                    ? !tenantScope.getExcludedTenantIds().contains(role.getTenantId())
                                    : tenantScope.getTenantIds().contains(role.getTenantId())));
            boolean platform = context.getPrincipalType() == PrincipalType.PLATFORM
                    && context.hasPermission(PlatformPermissions.TENANT_ROLES_READ)
                    && inScope;
            boolean tenant = context.getPrincipalType() == PrincipalType.TENANT
                    && role.getTenantId().equals(context.getTenantId())
                    && context.hasPermission(TenantPermissions.TENANT_ROLES_READ);
            String path = platform
                    ? "/platform/tenants/" + role.getTenantId() + "/roles/" + key.getResourceId()
                    : "/tenant/tenants/" + role.getTenantId() + "/roles/" + key.getResourceId();
            references.put(key, platform || tenant
                    ? new ResourceReference(
                            key,
                            label,
                            role.getTenantId(),
                            ResourceNavigationState.NAVIGABLE,
                            path,
                            null)
                    : new ResourceReference(
                            key,
                            null,
                            role.getTenantId(),
                            ResourceNavigationState.FORBIDDEN,
                            null,
                            forbiddenReason));
        }

        return Map.copyOf(references);
    }
}
