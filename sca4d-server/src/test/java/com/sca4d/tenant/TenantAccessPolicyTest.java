package com.sca4d.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.access.AccessAction;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.tenant.policy.TenantAccessPolicy;
import com.sca4d.tenant.policy.TenantActionScope;
import com.sca4d.tenant.policy.TenantResource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;

/** 验证不同访问等级允许的租户读取和修改操作。 */
class TenantAccessPolicyTest {

    @Test
    void shouldReuseParsedSchemaAndPolicyAcrossPolicyInstances() {
        TenantAccessPolicy first = new TenantAccessPolicy(Mockito.mock(TenantAccessService.class));
        TenantAccessPolicy second = new TenantAccessPolicy(Mockito.mock(TenantAccessService.class));

        assertThat(first.policy()).isSameAs(second.policy());
    }

    @Test
    void shouldResolveLimitedPlatformScopeForTenantResources() {
        TenantAccessService tenantAccessService = Mockito.mock(TenantAccessService.class);
        Mockito.when(tenantAccessService.scope(10L, AccessAction.WRITE))
                .thenReturn(new TenantActionScope(false, Set.of(20L), Set.of()));
        CurrentRequest request = new CurrentRequest();
        request.setPrincipal(Principal.of(10L, PrincipalType.PLATFORM, 1L, null));

        TenantAccessPolicy policy = new TenantAccessPolicy(tenantAccessService);
        TenantResource resource = policy.resource(request, AccessAction.WRITE, "20");
        assertThat(policy.policy().allows(request, AccessAction.WRITE, resource)).isTrue();
        var criteria = policy.criteria(request, AccessAction.WRITE, resource);

        assertThat(criteria.isAllData()).isFalse();
        assertThat(criteria.getTenantIds()).containsExactly(20L);
        assertThat(criteria.getTargetTenantId()).isEqualTo(20L);
    }

    @Test
    void shouldLimitTenantPrincipalAndRejectDefaultPrincipal() {
        TenantAccessPolicy policy = new TenantAccessPolicy(Mockito.mock(TenantAccessService.class));
        CurrentRequest tenantRequest = new CurrentRequest();
        tenantRequest.setPrincipal(Principal.of(10L, PrincipalType.TENANT, 1L, 20L));

        TenantResource tenantCollection = policy.resource(tenantRequest, AccessAction.MANAGE, null);
        assertThat(policy.policy().allows(tenantRequest, AccessAction.MANAGE, tenantCollection)).isTrue();
        assertThat(policy.criteria(tenantRequest, AccessAction.MANAGE, tenantCollection).getTenantIds())
                .containsExactly(20L);

        TenantResource otherTenant = policy.resource(tenantRequest, AccessAction.MANAGE, "21");
        assertThat(policy.policy().allows(tenantRequest, AccessAction.MANAGE, otherTenant)).isFalse();

        CurrentRequest defaultRequest = new CurrentRequest();
        defaultRequest.setPrincipal(Principal.of(10L, PrincipalType.DEFAULT, 1L, null));
        TenantResource defaultCollection = policy.resource(defaultRequest, AccessAction.READ, null);
        assertThat(policy.policy().allows(defaultRequest, AccessAction.READ, defaultCollection)).isFalse();
    }

    @Test
    void shouldPreserveExactExclusionFromAllTenantDefault() {
        TenantAccessService tenantAccessService = Mockito.mock(TenantAccessService.class);
        Mockito.when(tenantAccessService.scope(10L, AccessAction.WRITE))
                .thenReturn(new TenantActionScope(true, Set.of(), Set.of(20L)));
        CurrentRequest request = new CurrentRequest();
        request.setPrincipal(Principal.of(10L, PrincipalType.PLATFORM, 1L, null));

        TenantAccessPolicy policy = new TenantAccessPolicy(tenantAccessService);
        TenantResource resource = policy.resource(request, AccessAction.WRITE, null);
        assertThat(policy.policy().allows(request, AccessAction.WRITE, resource)).isTrue();
        var criteria = policy.criteria(request, AccessAction.WRITE, resource);

        assertThat(criteria.isAllData()).isTrue();
        assertThat(criteria.getExcludedTenantIds()).containsExactly(20L);
    }
}
