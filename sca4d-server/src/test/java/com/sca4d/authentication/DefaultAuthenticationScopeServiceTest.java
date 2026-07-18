package com.sca4d.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.error.AuthenticationErrorCode;
import com.sca4d.authentication.service.DefaultAuthenticationScopeService;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.exception.BizException;
import com.sca4d.tenant.Tenant;
import com.sca4d.tenant.TenantService;
import org.junit.jupiter.api.Test;

/** 验证公开租户编码只在后端认证入口转换为内部租户主键。 */
class DefaultAuthenticationScopeServiceTest {

    @Test
    void shouldResolveGlobalWithoutReadingTenant() {
        TenantService tenants = mock(TenantService.class);

        AuthenticationScope scope = new DefaultAuthenticationScopeService(tenants)
                .resolve(AccountScopeType.GLOBAL, null);

        assertThat(scope.getScopeType()).isEqualTo(AccountScopeType.GLOBAL);
        assertThat(scope.getScopeId()).isNull();
        verifyNoInteractions(tenants);
    }

    @Test
    void shouldResolveActiveTenantCodeToInternalId() {
        TenantService tenants = mock(TenantService.class);
        Tenant tenant = new Tenant();
        tenant.setTenantId(20L);
        when(tenants.requireActiveByCode("acme")).thenReturn(tenant);

        AuthenticationScope scope = new DefaultAuthenticationScopeService(tenants)
                .resolve(AccountScopeType.TENANT, "acme");

        assertThat(scope.getScopeType()).isEqualTo(AccountScopeType.TENANT);
        assertThat(scope.getScopeId()).isEqualTo(20L);
        verify(tenants).requireActiveByCode("acme");
    }

    @Test
    void shouldRejectMismatchedScopeSelection() {
        DefaultAuthenticationScopeService service = new DefaultAuthenticationScopeService(
                mock(TenantService.class));

        assertThatThrownBy(() -> service.resolve(AccountScopeType.GLOBAL, "acme"))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(AuthenticationErrorCode.AUTHENTICATION_SCOPE_INVALID));
        assertThatThrownBy(() -> service.resolve(AccountScopeType.TENANT, null))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(AuthenticationErrorCode.AUTHENTICATION_SCOPE_INVALID));
    }

    @Test
    void shouldRejectInvalidResolvedScope() {
        assertThatThrownBy(() -> new AuthenticationScope(null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthenticationScope(AccountScopeType.GLOBAL, 20L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthenticationScope(AccountScopeType.TENANT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
