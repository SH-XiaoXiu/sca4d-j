package com.sca4d.iam;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.SessionService;
import com.sca4d.common.auth.PasswordCredential;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.permission.PermissionCatalog;
import com.sca4d.common.permission.PermissionModule;
import com.sca4d.common.permission.PermissionNode;
import com.sca4d.config.properties.Sca4djBootstrapProperties;
import com.sca4d.entity.IamPrincipalEntity;
import com.sca4d.entity.IamSubjectEntity;
import com.sca4d.iam.account.AccountProvisioningService;
import com.sca4d.iam.account.ProvisionedAccount;
import com.sca4d.iam.bootstrap.IamBootstrap;
import com.sca4d.iam.principal.PrincipalService;
import com.sca4d.iam.principal.PrincipalStatus;
import com.sca4d.iam.repository.IamDefaultsRepository;
import com.sca4d.iam.repository.IamRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

/** 验证启动时同步权限目录并按配置创建首个平台管理员。 */
class IamBootstrapTest {

    @Test
    void shouldSynchronizePermissionsWithoutCreatingAdministratorWhenCredentialsAreAbsent() {
        IamRepository repository = mock(IamRepository.class);
        IamDefaultsRepository defaultsRepository = mock(IamDefaultsRepository.class);
        SessionService sessionService = mock(SessionService.class);
        PrincipalService principalService = mock(PrincipalService.class);
        AccountProvisioningService accountProvisioningService = mock(AccountProvisioningService.class);
        Sca4djBootstrapProperties properties = new Sca4djBootstrapProperties();
        PermissionCatalog catalog = new PermissionCatalog(
                List.of((PermissionModule) () -> PermissionNode.module("platform", "permission.platform", 10)));
        when(repository.requirePlatformAdministratorRoleId()).thenReturn(10L);
        when(principalService.hasPlatformPrincipal()).thenReturn(false);

        new IamBootstrap(catalog, defaultsRepository, repository, sessionService, principalService, accountProvisioningService, properties)
                .synchronize(null);

        verify(defaultsRepository).lockSynchronization();
        verify(repository).synchronizePermissionCatalog(catalog);
        verify(defaultsRepository).synchronizeDefaults();
        verify(repository).requirePlatformAdministratorRoleId();
        verify(sessionService).refreshOnlineAfterCommit();
        verify(accountProvisioningService, never()).create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        InOrder synchronization = inOrder(defaultsRepository, repository);
        synchronization.verify(defaultsRepository).lockSynchronization();
        synchronization.verify(repository).synchronizePermissionCatalog(catalog);
        synchronization.verify(defaultsRepository).synchronizeDefaults();
        synchronization.verify(repository).requirePlatformAdministratorRoleId();
    }

    @Test
    void shouldCreateFirstAdministratorOnlyWhenNoPlatformPrincipalAndCredentialsAreConfigured() {
        IamRepository repository = mock(IamRepository.class);
        IamDefaultsRepository defaultsRepository = mock(IamDefaultsRepository.class);
        SessionService sessionService = mock(SessionService.class);
        PrincipalService principalService = mock(PrincipalService.class);
        AccountProvisioningService accountProvisioningService = mock(AccountProvisioningService.class);
        Sca4djBootstrapProperties properties = new Sca4djBootstrapProperties();
        properties.setPlatformAdministratorIdentifier("root");
        properties.setPlatformAdministratorPassword("secret");
        properties.setPlatformAdministratorDisplayName("ROOT");
        PermissionCatalog catalog = new PermissionCatalog(
                List.of((PermissionModule) () -> PermissionNode.module("platform", "permission.platform", 10)));
        IamPrincipalEntity administrator = new IamPrincipalEntity().setId(20L).setSubjectId(20L).setTenantId(null).setMembershipId(null)
                .setPrincipalType(PrincipalType.PLATFORM).setDisplayName("ROOT").setStatus(PrincipalStatus.ACTIVE).setVersion(0L);
        ProvisionedAccount account = new ProvisionedAccount().setSubject(new IamSubjectEntity().setId(20L));
        when(repository.requirePlatformAdministratorRoleId()).thenReturn(10L);
        when(principalService.hasPlatformPrincipal()).thenReturn(false);
        when(accountProvisioningService.create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(account);
        when(principalService.createPlatform(20L, "ROOT")).thenReturn(administrator);

        new IamBootstrap(catalog, defaultsRepository, repository, sessionService, principalService, accountProvisioningService, properties)
                .synchronize(null);

        verify(accountProvisioningService).create(
                "root", new PasswordCredential("secret"), com.sca4d.common.auth.AccountScopeType.GLOBAL,
                null, "ROOT", null, null, null, null, null);
        verify(principalService).createPlatform(20L, "ROOT");
        verify(repository).replacePrincipalRoles(20L, List.of(10L));
    }

    @Test
    void shouldNotReplaceDisabledRootByCreatingAnotherPlatformPrincipal() {
        IamRepository repository = mock(IamRepository.class);
        IamDefaultsRepository defaultsRepository = mock(IamDefaultsRepository.class);
        SessionService sessionService = mock(SessionService.class);
        PrincipalService principalService = mock(PrincipalService.class);
        AccountProvisioningService accountProvisioningService = mock(AccountProvisioningService.class);
        Sca4djBootstrapProperties properties = new Sca4djBootstrapProperties();
        properties.setPlatformAdministratorIdentifier("root");
        properties.setPlatformAdministratorPassword("secret");
        PermissionCatalog catalog = new PermissionCatalog(
                List.of((PermissionModule) () -> PermissionNode.module("platform", "permission.platform", 10)));
        when(repository.requirePlatformAdministratorRoleId()).thenReturn(10L);
        when(principalService.hasPlatformPrincipal()).thenReturn(true);

        new IamBootstrap(catalog, defaultsRepository, repository, sessionService, principalService, accountProvisioningService, properties)
                .synchronize(null);

        verify(accountProvisioningService, never()).create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
