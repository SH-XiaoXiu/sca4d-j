package com.sca4d.iam;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.SessionService;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.exception.BizException;
import com.sca4d.entity.IamPrincipalEntity;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.principal.PrincipalStatus;
import com.sca4d.iam.principal.service.DefaultPrincipalService;
import com.sca4d.iam.repository.IamRepository;
import com.sca4d.tenant.MembershipStatus;
import com.sca4d.tenant.TenantService;
import com.sca4d.tenant.repository.TenantAccessRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/** 验证 Principal 创建、选择、状态修改和角色关系更新。 */
class DefaultPrincipalServiceTest {

    @Test
    void shouldRefreshAllIdentitiesAssignedToChangedRole() {
        IamRepository repository = mock(IamRepository.class);
        SessionService sessionService = mock(SessionService.class);
        when(repository.findSubjectIdsAssignedToRole(10L)).thenReturn(List.of(11L, 12L));

        new DefaultPrincipalService(repository, sessionService, mock(TenantService.class), mock(TenantAccessRepository.class))
                .refreshRoleSessions(10L);

        verify(sessionService).refreshAfterCommit(List.of(11L, 12L));
    }

    @Test
    void shouldRefreshAllTenantMemberSessions() {
        IamRepository repository = mock(IamRepository.class);
        SessionService sessionService = mock(SessionService.class);
        when(repository.findTenantSubjectIds(20L)).thenReturn(List.of(11L, 12L));

        new DefaultPrincipalService(repository, sessionService, mock(TenantService.class), mock(TenantAccessRepository.class))
                .refreshTenantSessions(20L);

        verify(sessionService).refreshAfterCommit(List.of(11L, 12L));
    }

    @Test
    void shouldPropagateConcurrentPrincipalCreationConflictFromRepository() {
        IamRepository repository = mock(IamRepository.class);
        when(repository.findPrincipal(11L, PrincipalType.PLATFORM, null)).thenReturn(null);
        when(repository.createPrincipal(11L, PrincipalType.PLATFORM, null, "管理员"))
                .thenThrow(new BizException(IamErrorCode.PRINCIPAL_EXISTS));
        DefaultPrincipalService service = new DefaultPrincipalService(
                repository, mock(SessionService.class), mock(TenantService.class), mock(TenantAccessRepository.class));

        assertThatThrownBy(() -> service.createPlatform(11L, "管理员"))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(IamErrorCode.PRINCIPAL_EXISTS));
    }

    @Test
    void shouldGrantExplicitAllTenantAccessToNewPlatformPrincipal() {
        IamRepository repository = mock(IamRepository.class);
        when(repository.findPrincipal(11L, PrincipalType.PLATFORM, null)).thenReturn(null);
        when(repository.createPrincipal(11L, PrincipalType.PLATFORM, null, "管理员"))
                .thenReturn(new IamPrincipalEntity().setId(30L).setSubjectId(11L).setTenantId(null).setMembershipId(null)
                        .setPrincipalType(PrincipalType.PLATFORM).setDisplayName("管理员").setStatus(PrincipalStatus.ACTIVE).setVersion(0L));
        TenantAccessRepository tenantAccessRepository = mock(TenantAccessRepository.class);
        DefaultPrincipalService service = new DefaultPrincipalService(
                repository, mock(SessionService.class), mock(TenantService.class), tenantAccessRepository);

        service.createPlatform(11L, "管理员");

        verify(tenantAccessRepository).grantAll(30L);
    }

    @Test
    void shouldCreateDelegatedPlatformPrincipalWithoutImplicitTenantAccess() {
        IamRepository repository = mock(IamRepository.class);
        SessionService sessionService = mock(SessionService.class);
        when(repository.findPrincipal(11L, PrincipalType.PLATFORM, null)).thenReturn(null);
        when(repository.createPrincipal(11L, PrincipalType.PLATFORM, null, "Alice"))
                .thenReturn(new IamPrincipalEntity().setId(30L).setSubjectId(11L).setTenantId(null).setMembershipId(null)
                        .setPrincipalType(PrincipalType.PLATFORM).setDisplayName("Alice").setStatus(PrincipalStatus.ACTIVE).setVersion(0L));
        TenantAccessRepository tenantAccessRepository = mock(TenantAccessRepository.class);
        DefaultPrincipalService service = new DefaultPrincipalService(
                repository, sessionService, mock(TenantService.class), tenantAccessRepository);

        service.createDelegatedPlatform(11L, "Alice");

        verify(repository).createPrincipal(11L, PrincipalType.PLATFORM, null, "Alice");
        verify(tenantAccessRepository, never()).grantAll(30L);
        verify(sessionService).refreshAfterCommit(List.of(11L));
    }

    @Test
    void shouldCreateTenantPrincipalWithRolesAndRefreshSessionOnce() {
        IamRepository repository = mock(IamRepository.class);
        SessionService sessionService = mock(SessionService.class);
        when(repository.findActiveTenantRoleIds(20L, List.of(40L)))
                .thenReturn(Set.of(40L));
        when(repository.createTenantPrincipal(11L, 20L, "Alice"))
                .thenReturn(new IamPrincipalEntity().setId(30L).setSubjectId(11L).setTenantId(20L).setMembershipId(31L).setPrincipalType(PrincipalType.TENANT)
                        .setDisplayName("Alice").setStatus(PrincipalStatus.ACTIVE).setVersion(0L));
        DefaultPrincipalService service = new DefaultPrincipalService(
                repository, sessionService, mock(TenantService.class), mock(TenantAccessRepository.class));

        service.createTenant(11L, 20L, "Alice", List.of(40L, 40L));

        verify(repository).replacePrincipalRoles(30L, List.of(40L));
        verify(sessionService, times(1)).refreshAfterCommit(List.of(11L));
    }

    @Test
    void shouldReplaceTenantRolesUsingExpectedPrincipalVersion() {
        IamRepository repository = mock(IamRepository.class);
        SessionService sessionService = mock(SessionService.class);
        when(repository.findPrincipalById(30L)).thenReturn(new IamPrincipalEntity().setId(30L).setSubjectId(11L).setTenantId(20L).setMembershipId(31L)
                .setPrincipalType(PrincipalType.TENANT).setDisplayName("Alice").setStatus(PrincipalStatus.ACTIVE).setVersion(0L));
        when(repository.findActiveTenantRoleIds(20L, List.of(40L)))
                .thenReturn(Set.of(40L));
        DefaultPrincipalService service = new DefaultPrincipalService(
                repository, sessionService, mock(TenantService.class), mock(TenantAccessRepository.class));

        service.replaceTenantRoles(30L, 20L, List.of(40L), 7L);

        verify(repository).replaceTenantPrincipalRoles(30L, 20L, List.of(40L), 7L);
        verify(sessionService).refreshAfterCommit(List.of(11L));
    }

    @Test
    void shouldChangeTenantPrincipalStatusWithinTenantScope() {
        IamRepository repository = mock(IamRepository.class);
        SessionService sessionService = mock(SessionService.class);
        when(repository.findPrincipalById(30L)).thenReturn(new IamPrincipalEntity().setId(30L).setSubjectId(11L).setTenantId(20L).setMembershipId(31L)
                .setPrincipalType(PrincipalType.TENANT).setDisplayName("Alice").setStatus(PrincipalStatus.ACTIVE).setVersion(0L));
        DefaultPrincipalService service = new DefaultPrincipalService(
                repository, sessionService, mock(TenantService.class), mock(TenantAccessRepository.class));

        service.changeTenantStatus(30L, 20L, MembershipStatus.DISABLED, 7L);

        verify(repository).updateTenantMembershipStatus(31L, 20L, MembershipStatus.DISABLED, 7L);
        verify(sessionService).refreshAfterCommit(List.of(11L));
    }

    @Test
    void shouldChangePlatformPrincipalStatusByPrincipalId() {
        IamRepository repository = mock(IamRepository.class);
        SessionService sessionService = mock(SessionService.class);
        when(repository.findPrincipalById(30L)).thenReturn(new IamPrincipalEntity().setId(30L).setSubjectId(11L).setTenantId(null).setMembershipId(null)
                .setPrincipalType(PrincipalType.PLATFORM).setDisplayName("Alice").setStatus(PrincipalStatus.ACTIVE).setVersion(0L));
        DefaultPrincipalService service = new DefaultPrincipalService(
                repository, sessionService, mock(TenantService.class), mock(TenantAccessRepository.class));

        service.changePlatformStatus(30L, PrincipalStatus.DISABLED, 7L);

        verify(repository).updatePrincipalStatus(30L, PrincipalStatus.DISABLED, 7L);
        verify(sessionService).refreshAfterCommit(List.of(11L));
    }

    @Test
    void shouldReplacePlatformRolesByPrincipalId() {
        IamRepository repository = mock(IamRepository.class);
        SessionService sessionService = mock(SessionService.class);
        when(repository.findPrincipalById(30L)).thenReturn(new IamPrincipalEntity().setId(30L).setSubjectId(11L).setTenantId(null).setMembershipId(null)
                .setPrincipalType(PrincipalType.PLATFORM).setDisplayName("Alice").setStatus(PrincipalStatus.ACTIVE).setVersion(0L));
        when(repository.findActivePlatformRoleIds(List.of(40L)))
                .thenReturn(Set.of(40L));
        DefaultPrincipalService service = new DefaultPrincipalService(
                repository, sessionService, mock(TenantService.class), mock(TenantAccessRepository.class));

        service.replacePlatformRoles(30L, List.of(40L, 40L), 7L);

        verify(repository).replacePlatformPrincipalRoles(30L, List.of(40L), 7L);
        verify(sessionService).refreshAfterCommit(List.of(11L));
    }

    @Test
    void shouldRejectTenantPrincipalThroughPlatformResource() {
        IamRepository repository = mock(IamRepository.class);
        when(repository.findPrincipalById(30L)).thenReturn(new IamPrincipalEntity().setId(30L).setSubjectId(11L).setTenantId(20L).setMembershipId(31L)
                .setPrincipalType(PrincipalType.TENANT).setDisplayName("Alice").setStatus(PrincipalStatus.ACTIVE).setVersion(0L));
        DefaultPrincipalService service = new DefaultPrincipalService(
                repository, mock(SessionService.class), mock(TenantService.class), mock(TenantAccessRepository.class));

        assertThatThrownBy(() -> service.replacePlatformRoles(30L, List.of(), 7L))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(IamErrorCode.PRINCIPAL_NOT_FOUND));
    }
}
