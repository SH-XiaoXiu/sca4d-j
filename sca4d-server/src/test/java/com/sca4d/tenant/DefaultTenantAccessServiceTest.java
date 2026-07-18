package com.sca4d.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.access.AccessAction;
import com.sca4d.common.access.ResourceAccessDeniedException;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.resource.ResourceReferenceResolver;
import com.sca4d.entity.IamPrincipalEntity;
import com.sca4d.iam.principal.PrincipalService;
import com.sca4d.iam.principal.PrincipalStatus;
import com.sca4d.iam.repository.IamRepository;
import com.sca4d.tenant.policy.TenantAccessGrant;
import com.sca4d.tenant.repository.TenantAccessRepository;
import com.sca4d.tenant.service.DefaultTenantAccessService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.List;
import java.util.Set;

/** 验证平台 Principal 的租户 ACL 配置和请求访问范围解析。 */
class DefaultTenantAccessServiceTest {

    @Test
    void shouldResolveDifferentLevelsForThreeTenants() {
        IamRepository iamRepository = mock(IamRepository.class);
        TenantAccessRepository accessRepository = mock(TenantAccessRepository.class);
        when(accessRepository.findGrants(10L)).thenReturn(List.of(
                new TenantAccessGrant(1L, AccessAction.WRITE),
                new TenantAccessGrant(2L, AccessAction.READ),
                new TenantAccessGrant(3L, AccessAction.MANAGE)));
        DefaultTenantAccessService service = new DefaultTenantAccessService(
                iamRepository,
                mock(PrincipalService.class),
                accessRepository,
                mock(ResourceReferenceResolver.class));

        assertThat(service.scope(10L, AccessAction.READ).getTenantIds())
                .containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(service.scope(10L, AccessAction.WRITE).getTenantIds())
                .containsExactlyInAnyOrder(1L, 3L);
        assertThat(service.scope(10L, AccessAction.MANAGE).getTenantIds())
                .containsExactly(3L);
    }

    @Test
    void shouldApplyExactLowerRuleOverAllTenantDefault() {
        TenantAccessRepository accessRepository = mock(TenantAccessRepository.class);
        when(accessRepository.findGrants(10L)).thenReturn(List.of(
                new TenantAccessGrant(null, AccessAction.MANAGE),
                new TenantAccessGrant(2L, AccessAction.READ)));
        DefaultTenantAccessService service = new DefaultTenantAccessService(
                mock(IamRepository.class),
                mock(PrincipalService.class),
                accessRepository,
                mock(ResourceReferenceResolver.class));

        assertThat(service.scope(10L, AccessAction.READ).isAllData()).isTrue();
        assertThat(service.scope(10L, AccessAction.READ).getExcludedTenantIds()).isEmpty();
        assertThat(service.scope(10L, AccessAction.WRITE).isAllData()).isTrue();
        assertThat(service.scope(10L, AccessAction.WRITE).getExcludedTenantIds()).containsExactly(2L);
        assertThat(service.scope(10L, AccessAction.MANAGE).getExcludedTenantIds()).containsExactly(2L);
    }

    @Test
    void shouldRejectGrantAboveActorEffectiveTenantLevel() {
        IamRepository iamRepository = mock(IamRepository.class);
        TenantAccessRepository accessRepository = mock(TenantAccessRepository.class);
        when(accessRepository.findGrants(10L)).thenReturn(List.of(
                new TenantAccessGrant(null, AccessAction.MANAGE),
                new TenantAccessGrant(2L, AccessAction.READ)));
        PrincipalService principalService = mock(PrincipalService.class);
        when(principalService.require(20L)).thenReturn(new IamPrincipalEntity().setId(20L).setSubjectId(100L).setTenantId(null).setMembershipId(null)
                .setPrincipalType(PrincipalType.PLATFORM).setDisplayName("Operator").setStatus(PrincipalStatus.ACTIVE).setVersion(3L));
        when(iamRepository.findExistingTenantIds(Set.of(2L))).thenReturn(Set.of(2L));
        DefaultTenantAccessService service = new DefaultTenantAccessService(
                iamRepository, principalService, accessRepository, mock(ResourceReferenceResolver.class));

        assertThatThrownBy(() -> service.replace(
                10L,
                20L,
                null,
                List.of(new TenantAccessGrant(2L, AccessAction.WRITE)),
                3L))
                .isInstanceOf(ResourceAccessDeniedException.class);
    }

    @Test
    void shouldReplaceCanonicalRulesAfterPrincipalVersionAdvance() {
        IamRepository iamRepository = mock(IamRepository.class);
        TenantAccessRepository accessRepository = mock(TenantAccessRepository.class);
        when(accessRepository.findGrants(10L))
                .thenReturn(List.of(new TenantAccessGrant(null, AccessAction.MANAGE)));
        PrincipalService principalService = mock(PrincipalService.class);
        when(principalService.require(20L)).thenReturn(new IamPrincipalEntity().setId(20L).setSubjectId(100L).setTenantId(null).setMembershipId(null)
                .setPrincipalType(PrincipalType.PLATFORM).setDisplayName("Operator").setStatus(PrincipalStatus.ACTIVE).setVersion(3L));
        when(iamRepository.findExistingTenantIds(Set.of(1L, 2L))).thenReturn(Set.of(1L, 2L));
        DefaultTenantAccessService service = new DefaultTenantAccessService(
                iamRepository, principalService, accessRepository, mock(ResourceReferenceResolver.class));

        service.replace(
                10L,
                20L,
                AccessAction.WRITE,
                List.of(
                        new TenantAccessGrant(1L, AccessAction.WRITE),
                        new TenantAccessGrant(2L, AccessAction.READ)),
                3L);

        verify(iamRepository).advancePlatformPrincipalVersionForTenantAccess(20L, 3L);
        verify(accessRepository).replace(
                ArgumentMatchers.eq(20L),
                ArgumentMatchers.eq(AccessAction.WRITE),
                ArgumentMatchers.argThat(grants -> grants.size() == 1
                        && grants.getFirst().getTenantId().equals(2L)
                        && grants.getFirst().getAccessAction() == AccessAction.READ));
    }
}
