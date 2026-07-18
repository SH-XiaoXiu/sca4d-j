package com.sca4d.tenant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sca4d.common.access.AccessAction;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.entity.IamPrincipalEntity;
import com.sca4d.iam.account.AccountProvisioningService;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.principal.PrincipalService;
import com.sca4d.tenant.policy.TenantMemberAccessPolicy;
import com.sca4d.tenant.repository.TenantMemberRepository;
import com.sca4d.tenant.service.DefaultTenantMemberService;
import org.junit.jupiter.api.Test;

import java.util.List;

/** 验证租户成员添加、状态修改、角色替换和删除。 */
class DefaultTenantMemberServiceTest {

    @Test
    void shouldDisableOnlyTargetTenantPrincipal() {
        TenantService tenantService = mock(TenantService.class);
        AccountProvisioningService accountProvisioningService = mock(AccountProvisioningService.class);
        AccountService accountService = mock(AccountService.class);
        PrincipalService principalService = mock(PrincipalService.class);
        TenantMemberAccessPolicy accessPolicy = mock(TenantMemberAccessPolicy.class);
        TenantMemberRepository repository = mock(TenantMemberRepository.class);
        TenantMember member = new TenantMember();
        member.setTenantId(20L);
        member.setPrincipalId(30L);
        when(repository.find(20L, 30L)).thenReturn(member);
        DefaultTenantMemberService service = new DefaultTenantMemberService(
                tenantService, accountProvisioningService, accountService, principalService, accessPolicy, repository);

        service.changeStatus(20L, 30L, MembershipStatus.DISABLED, 4L);

        verify(accessPolicy).require(20L, AccessAction.WRITE);
        verify(principalService).changeTenantStatus(30L, 20L, MembershipStatus.DISABLED, 4L);
        verifyNoInteractions(accountProvisioningService);
    }

    @Test
    void shouldPassMemberVersionWhenReplacingRoles() {
        TenantService tenantService = mock(TenantService.class);
        AccountProvisioningService accountProvisioningService = mock(AccountProvisioningService.class);
        AccountService accountService = mock(AccountService.class);
        PrincipalService principalService = mock(PrincipalService.class);
        TenantMemberAccessPolicy accessPolicy = mock(TenantMemberAccessPolicy.class);
        TenantMemberRepository repository = mock(TenantMemberRepository.class);
        TenantMember member = new TenantMember();
        member.setTenantId(20L);
        member.setPrincipalId(30L);
        when(repository.find(20L, 30L)).thenReturn(member);
        DefaultTenantMemberService service = new DefaultTenantMemberService(
                tenantService, accountProvisioningService, accountService, principalService, accessPolicy, repository);

        service.replaceRoles(20L, 30L, List.of(40L), 5L);

        verify(accessPolicy).require(20L, AccessAction.MANAGE);
        verify(principalService).replaceTenantRoles(30L, 20L, List.of(40L), 5L);
    }

    @Test
    void shouldAddExistingAccountOnlyThroughTenantMemberWorkflow() {
        TenantService tenantService = mock(TenantService.class);
        AccountProvisioningService accountProvisioningService = mock(AccountProvisioningService.class);
        AccountService accountService = mock(AccountService.class);
        PrincipalService principalService = mock(PrincipalService.class);
        TenantMemberAccessPolicy accessPolicy = mock(TenantMemberAccessPolicy.class);
        TenantMemberRepository repository = mock(TenantMemberRepository.class);
        IamAccountEntity account = new IamAccountEntity()
                .setId(40L)
                .setSubjectId(50L)
                .setScopeType(AccountScopeType.GLOBAL);
        IamPrincipalEntity principal = new IamPrincipalEntity().setId(60L).setSubjectId(50L);
        when(accountService.require(40L)).thenReturn(account);
        when(principalService.createTenant(50L, 20L, "租户成员", List.of(70L))).thenReturn(principal);
        DefaultTenantMemberService service = new DefaultTenantMemberService(
                tenantService, accountProvisioningService, accountService, principalService, accessPolicy, repository);

        Long principalId = service.addExistingAccount(20L, 40L, "租户成员", List.of(70L));

        org.assertj.core.api.Assertions.assertThat(principalId).isEqualTo(60L);
        verify(accessPolicy).require(20L, AccessAction.WRITE);
        verify(tenantService).requireActive(20L);
        verify(principalService).createTenant(50L, 20L, "租户成员", List.of(70L));
        verifyNoInteractions(accountProvisioningService);
    }

    @Test
    void shouldReadAssignableRolesThroughPrincipalService() {
        TenantService tenantService = mock(TenantService.class);
        AccountProvisioningService accountProvisioningService = mock(AccountProvisioningService.class);
        AccountService accountService = mock(AccountService.class);
        PrincipalService principalService = mock(PrincipalService.class);
        TenantMemberAccessPolicy accessPolicy = mock(TenantMemberAccessPolicy.class);
        TenantMemberRepository repository = mock(TenantMemberRepository.class);
        List<TenantRole> roles = List.of(new TenantRole(70L, "TENANT_ADMIN", "租户管理员"));
        when(principalService.assignableTenantRoles(20L)).thenReturn(roles);
        DefaultTenantMemberService service = new DefaultTenantMemberService(
                tenantService, accountProvisioningService, accountService, principalService, accessPolicy, repository);

        org.assertj.core.api.Assertions.assertThat(service.assignableRoles(20L)).isSameAs(roles);

        verify(tenantService).require(20L);
        verify(accessPolicy).require(20L, AccessAction.READ);
        verify(principalService).assignableTenantRoles(20L);
        verifyNoInteractions(repository);
    }

}
