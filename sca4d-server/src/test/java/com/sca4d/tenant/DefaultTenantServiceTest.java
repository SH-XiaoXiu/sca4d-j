package com.sca4d.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.SessionService;
import com.sca4d.common.exception.BizException;
import com.sca4d.iam.repository.IamRepository;
import com.sca4d.iam.tenant.TenantStatus;
import com.sca4d.iam.tenant.TenantType;
import com.sca4d.system.repository.DictionaryRepository;
import com.sca4d.tenant.service.DefaultTenantService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

/** 验证租户创建、修改、停用和删除。 */
class DefaultTenantServiceTest {

    @Test
    void shouldCreateBuiltInAdministratorRoleWithTenantPermissions() {
        IamRepository repository = mock(IamRepository.class);
        when(repository.createTenant("acme", "Acme", TenantType.DEFAULT))
                .thenReturn(20L);

        Long tenantId = new DefaultTenantService(
                repository, mock(DictionaryRepository.class), mock(SessionService.class))
                .create("acme", "Acme", TenantType.DEFAULT);

        assertThat(tenantId).isEqualTo(20L);
        verify(repository).createTenantRoleWithPermissions(
                20L, TenantPermissions.TENANT_ADMIN_ROLE, "租户管理员", TenantPermissions.MODULE);
    }

    @Test
    void shouldChangeOnlyTenantStatusWhenTenantIsDisabled() {
        IamRepository repository = mock(IamRepository.class);
        when(repository.findTenant(20L)).thenReturn(mock(Tenant.class));
        SessionService sessionService = mock(SessionService.class);
        DefaultTenantService service = new DefaultTenantService(
                repository, mock(DictionaryRepository.class), sessionService);

        service.changeStatus(20L, TenantStatus.DISABLED, 3L);

        verify(repository).findTenant(20L);
        verify(repository).findTenantSubjectIds(20L);
        verify(repository).updateTenantStatus(20L, TenantStatus.DISABLED, 3L);
        verify(sessionService).refreshAfterCommit(List.of());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldResolveOnlyActiveTenantByCurrentCode() {
        IamRepository repository = mock(IamRepository.class);
        Tenant active = new Tenant();
        active.setTenantId(20L);
        active.setCode("acme");
        active.setStatus(TenantStatus.ACTIVE);
        when(repository.findTenantByCode("acme")).thenReturn(active);
        DefaultTenantService service = new DefaultTenantService(
                repository, mock(DictionaryRepository.class), mock(SessionService.class));

        assertThat(service.requireActiveByCode(" acme ")).isSameAs(active);

        active.setStatus(TenantStatus.DISABLED);
        assertThatThrownBy(() -> service.requireActiveByCode("acme"))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(TenantErrorCode.TENANT_UNAVAILABLE));
    }

    @Test
    void shouldUpdateValidatedTenantCodeAndNameTogether() {
        IamRepository repository = mock(IamRepository.class);
        Tenant tenant = new Tenant();
        tenant.setTenantId(20L);
        tenant.setCode("acme");
        when(repository.findTenant(20L)).thenReturn(tenant);
        DefaultTenantService service = new DefaultTenantService(
                repository, mock(DictionaryRepository.class), mock(SessionService.class));

        service.update(20L, " acme-new ", " Acme New ", 3L);

        verify(repository).findTenant(20L);
        verify(repository).updateTenant(20L, "acme-new", "Acme New", 3L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldIsolateTenantAndRefreshCapturedSessionsAfterCommit() {
        IamRepository repository = mock(IamRepository.class);
        DictionaryRepository dictionaryRepository = mock(DictionaryRepository.class);
        SessionService sessionService = mock(SessionService.class);
        when(repository.findTenant(20L)).thenReturn(new Tenant());
        when(repository.isolateDeletedTenant(20L, 3L)).thenReturn(List.of(31L, 32L));
        DefaultTenantService service = new DefaultTenantService(
                repository, dictionaryRepository, sessionService);

        service.delete(20L, 3L);

        InOrder operations = inOrder(repository, dictionaryRepository, sessionService);
        operations.verify(repository).findTenant(20L);
        operations.verify(repository).isolateDeletedTenant(20L, 3L);
        operations.verify(dictionaryRepository).isolateTenantDictionaries(20L);
        operations.verify(sessionService).refreshAfterCommit(List.of(31L, 32L));
    }
}
