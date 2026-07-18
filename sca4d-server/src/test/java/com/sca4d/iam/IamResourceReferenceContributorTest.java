package com.sca4d.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.access.AccessAction;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.i18n.LocalizedMessagesFixture;
import com.sca4d.common.resource.ResourceNavigationState;
import com.sca4d.common.resource.ResourceReferenceContext;
import com.sca4d.common.resource.ResourceReferenceKey;
import com.sca4d.iam.repository.IamRepository;
import com.sca4d.iam.resource.AccountReference;
import com.sca4d.iam.resource.IamResourceReferenceContributor;
import com.sca4d.iam.resource.IamResourceTypes;
import com.sca4d.iam.resource.PrincipalReference;
import com.sca4d.iam.resource.RoleReference;
import com.sca4d.platform.PlatformPermissions;
import com.sca4d.tenant.TenantPermissions;
import com.sca4d.tenant.policy.TenantAccessGrant;
import com.sca4d.tenant.repository.TenantAccessRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/** 验证 IAM 资源引用名称、存在状态和导航地址解析。 */
class IamResourceReferenceContributorTest {

    @Test
    void shouldResolveAccountAuditTargetToItsManagementDetail() {
        IamRepository repository = mock(IamRepository.class);
        AccountReference globalAccount = new AccountReference(
                80L, "平台账号", com.sca4d.common.auth.AccountScopeType.GLOBAL, null, false);
        AccountReference tenantAccount = new AccountReference(
                81L, "租户账号", com.sca4d.common.auth.AccountScopeType.TENANT, 20L, false);
        when(repository.findAccountReferences(Set.of(80L, 81L)))
                .thenReturn(List.of(globalAccount, tenantAccount));
        TenantAccessRepository tenantAccessRepository = mock(TenantAccessRepository.class);
        when(tenantAccessRepository.findGrants(100L))
                .thenReturn(List.of(new TenantAccessGrant(20L, AccessAction.READ)));
        IamResourceReferenceContributor contributor = new IamResourceReferenceContributor(
                repository, tenantAccessRepository, LocalizedMessagesFixture.create());
        ResourceReferenceKey globalKey = new ResourceReferenceKey(IamResourceTypes.ACCOUNT, "80");
        ResourceReferenceKey tenantKey = new ResourceReferenceKey(IamResourceTypes.ACCOUNT, "81");

        var references = contributor.resolve(
                Set.of(globalKey, tenantKey),
                new ResourceReferenceContext(
                        PrincipalType.PLATFORM,
                        100L,
                        null,
                        Set.of(PlatformPermissions.ACCOUNTS_READ, PlatformPermissions.TENANT_MEMBERS_READ)));

        assertThat(references.get(globalKey).getLabel()).isEqualTo("平台账号");
        assertThat(references.get(globalKey).getPath()).isEqualTo("/platform/accounts/80");
        assertThat(references.get(tenantKey).getLabel()).isEqualTo("租户账号");
        assertThat(references.get(tenantKey).getPath()).isEqualTo("/platform/tenants/20/accounts/81");
    }

    @Test
    void shouldResolveCurrentTenantMemberInsideTenantWorkspace() {
        IamRepository repository = mock(IamRepository.class);
        PrincipalReference member = new PrincipalReference(
                11L, 1000L, 20L, PrincipalType.TENANT, "当前成员", false);
        when(repository.findPrincipalReferences(Set.of(11L))).thenReturn(List.of(member));
        IamResourceReferenceContributor contributor = new IamResourceReferenceContributor(
                repository, mock(TenantAccessRepository.class), LocalizedMessagesFixture.create());
        ResourceReferenceKey key = new ResourceReferenceKey(IamResourceTypes.TENANT_MEMBER, "11");

        var reference = contributor.resolve(
                Set.of(key),
                new ResourceReferenceContext(
                        PrincipalType.TENANT,
                        100L,
                        20L,
                        Set.of(TenantPermissions.TENANT_MEMBERS_READ)))
                .get(key);

        assertThat(reference.getNavigationState()).isEqualTo(ResourceNavigationState.NAVIGABLE);
        assertThat(reference.getPath()).isEqualTo("/tenant/tenants/20/members/11");
    }

    @Test
    void shouldResolvePlatformOperatorOrTenantMemberDetail() {
        IamRepository repository = mock(IamRepository.class);
        PrincipalReference platformPrincipal = new PrincipalReference(
                10L, 1000L, null, PrincipalType.PLATFORM, "平台主体", false);
        PrincipalReference tenantPrincipal = new PrincipalReference(
                11L, 1001L, 20L, PrincipalType.TENANT, "租户成员", false);
        when(repository.findPrincipalReferences(Set.of(10L, 11L)))
                .thenReturn(List.of(platformPrincipal, tenantPrincipal));
        TenantAccessRepository tenantAccessRepository = mock(TenantAccessRepository.class);
        when(tenantAccessRepository.findGrants(100L))
                .thenReturn(List.of(new TenantAccessGrant(20L, AccessAction.READ)));
        IamResourceReferenceContributor contributor = new IamResourceReferenceContributor(
                repository, tenantAccessRepository, LocalizedMessagesFixture.create());
        ResourceReferenceKey platformKey = new ResourceReferenceKey(IamResourceTypes.PRINCIPAL, "10");
        ResourceReferenceKey tenantKey = new ResourceReferenceKey(IamResourceTypes.PRINCIPAL, "11");

        var references = contributor.resolve(
                Set.of(platformKey, tenantKey),
                new ResourceReferenceContext(
                        PrincipalType.PLATFORM,
                        100L,
                        null,
                        Set.of(PlatformPermissions.OPERATORS_READ, PlatformPermissions.TENANT_MEMBERS_READ)));

        assertThat(references.get(platformKey).getPath())
                .isEqualTo("/platform/operators/10");
        assertThat(references.get(tenantKey).getPath())
                .isEqualTo("/platform/tenants/20/members/11");
        verify(repository).findPrincipalReferences(Set.of(10L, 11L));
    }

    @Test
    void shouldResolvePlatformAndManagedTenantRolePathsWithOneRoleQuery() {
        IamRepository repository = mock(IamRepository.class);
        RoleReference platformRole = new RoleReference(
                10L, null, "AUDITOR", "平台审计员", false);
        RoleReference tenantRole = new RoleReference(
                11L, 20L, "OPERATOR", "租户操作员", false);
        when(repository.findRoleReferences(Set.of(10L, 11L)))
                .thenReturn(List.of(platformRole, tenantRole));
        TenantAccessRepository tenantAccessRepository = mock(TenantAccessRepository.class);
        when(tenantAccessRepository.findGrants(100L))
                .thenReturn(List.of(new TenantAccessGrant(null, AccessAction.MANAGE)));
        IamResourceReferenceContributor contributor = new IamResourceReferenceContributor(
                repository, tenantAccessRepository, LocalizedMessagesFixture.create());
        ResourceReferenceKey platformKey = new ResourceReferenceKey(IamResourceTypes.ROLE, "10");
        ResourceReferenceKey tenantKey = new ResourceReferenceKey(IamResourceTypes.ROLE, "11");

        var references = contributor.resolve(
                Set.of(platformKey, tenantKey),
                new ResourceReferenceContext(
                        PrincipalType.PLATFORM,
                        100L,
                        null,
                        Set.of(PlatformPermissions.ROLES_READ, PlatformPermissions.TENANT_ROLES_READ)));

        assertThat(references.get(platformKey).getLabel()).isEqualTo("平台审计员");
        assertThat(references.get(platformKey).getPath()).isEqualTo("/platform/roles/10");
        assertThat(references.get(tenantKey).getLabel()).isEqualTo("租户操作员");
        assertThat(references.get(tenantKey).getTenantId()).isEqualTo(20L);
        assertThat(references.get(tenantKey).getPath()).isEqualTo("/platform/tenants/20/roles/11");
        verify(repository).findRoleReferences(Set.of(10L, 11L));
    }

    @Test
    void shouldAllowOnlyCurrentTenantRolePathForTenantPrincipal() {
        IamRepository repository = mock(IamRepository.class);
        RoleReference current = new RoleReference(
                11L, 20L, "CURRENT", "当前租户角色", false);
        RoleReference foreign = new RoleReference(
                12L, 30L, "FOREIGN", "其他租户角色", false);
        when(repository.findRoleReferences(Set.of(11L, 12L))).thenReturn(List.of(current, foreign));
        IamResourceReferenceContributor contributor = new IamResourceReferenceContributor(
                repository, mock(TenantAccessRepository.class), LocalizedMessagesFixture.create());
        ResourceReferenceKey currentKey = new ResourceReferenceKey(IamResourceTypes.ROLE, "11");
        ResourceReferenceKey foreignKey = new ResourceReferenceKey(IamResourceTypes.ROLE, "12");

        var references = contributor.resolve(
                Set.of(currentKey, foreignKey),
                new ResourceReferenceContext(
                        PrincipalType.TENANT, 200L, 20L, Set.of(TenantPermissions.TENANT_ROLES_READ)));

        assertThat(references.get(currentKey).getNavigationState())
                .isEqualTo(ResourceNavigationState.NAVIGABLE);
        assertThat(references.get(currentKey).getPath()).isEqualTo("/tenant/tenants/20/roles/11");
        assertThat(references.get(foreignKey).getNavigationState())
                .isEqualTo(ResourceNavigationState.FORBIDDEN);
        assertThat(references.get(foreignKey).getLabel()).isNull();
        assertThat(references.get(foreignKey).getPath()).isNull();
    }

    @Test
    void shouldHideOutOfScopeTenantRoleFromLimitedPlatformPrincipal() {
        IamRepository repository = mock(IamRepository.class);
        RoleReference allowed = new RoleReference(11L, 20L, "ALLOWED", "允许角色", false);
        RoleReference forbidden = new RoleReference(12L, 30L, "FORBIDDEN", "越权角色", false);
        when(repository.findRoleReferences(Set.of(11L, 12L))).thenReturn(List.of(allowed, forbidden));
        TenantAccessRepository tenantAccessRepository = mock(TenantAccessRepository.class);
        when(tenantAccessRepository.findGrants(100L))
                .thenReturn(List.of(new TenantAccessGrant(20L, AccessAction.READ)));
        IamResourceReferenceContributor contributor = new IamResourceReferenceContributor(
                repository, tenantAccessRepository, LocalizedMessagesFixture.create());

        var references = contributor.resolve(
                Set.of(new ResourceReferenceKey(IamResourceTypes.ROLE, "11"), new ResourceReferenceKey(IamResourceTypes.ROLE, "12")),
                new ResourceReferenceContext(
                        PrincipalType.PLATFORM, 100L, null, Set.of(PlatformPermissions.TENANT_ROLES_READ)));

        assertThat(references.get(new ResourceReferenceKey(IamResourceTypes.ROLE, "11")).getNavigationState())
                .isEqualTo(ResourceNavigationState.NAVIGABLE);
        assertThat(references.get(new ResourceReferenceKey(IamResourceTypes.ROLE, "12")).getNavigationState())
                .isEqualTo(ResourceNavigationState.FORBIDDEN);
        assertThat(references.get(new ResourceReferenceKey(IamResourceTypes.ROLE, "12")).getLabel()).isNull();
    }

}
