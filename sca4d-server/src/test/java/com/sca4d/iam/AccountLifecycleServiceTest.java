package com.sca4d.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.Authentication;
import com.sca4d.common.exception.BizException;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.entity.IamSubjectEntity;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.account.service.AccountLifecycleService;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.repository.IamRepository;
import com.sca4d.iam.subject.SubjectService;
import org.junit.jupiter.api.Test;

import java.util.List;

/** 验证账号停用和删除不会切断仍在使用的 Principal 登录入口。 */
class AccountLifecycleServiceTest {

    @Test
    void shouldRejectDisablingLastReachableAccount() {
        AccountService accounts = mock(AccountService.class);
        IamRepository repository = mock(IamRepository.class);
        Authentication authentication = mock(Authentication.class);
        IamAccountEntity account = new IamAccountEntity()
                .setId(80L)
                .setSubjectId(70L)
                .setScopeType(AccountScopeType.GLOBAL)
                .setStatus(AccountStatus.ACTIVE)
                .setVersion(4L);
        when(accounts.require(80L)).thenReturn(account);
        when(repository.hasUnreachableActivePrincipal(70L, 80L)).thenReturn(true);
        AccountLifecycleService service = new AccountLifecycleService(
                accounts, mock(SubjectService.class), repository, authentication);

        assertThatThrownBy(() -> service.changeStatus(80L, AccountStatus.DISABLED, 4L))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(IamErrorCode.ACCOUNT_ACCESS_WOULD_BE_LOST));

        verify(repository, never()).updateAccountStatus(80L, AccountStatus.DISABLED, 4L);
        verify(authentication, never()).endSessions(80L);
    }

    @Test
    void shouldDisableAccountAndEndItsSessionsWhenOtherLoginEntryRemains() {
        AccountService accounts = mock(AccountService.class);
        IamRepository repository = mock(IamRepository.class);
        Authentication authentication = mock(Authentication.class);
        IamAccountEntity account = new IamAccountEntity()
                .setId(80L)
                .setSubjectId(70L)
                .setScopeType(AccountScopeType.GLOBAL)
                .setStatus(AccountStatus.ACTIVE)
                .setVersion(4L);
        IamAccountEntity changed = new IamAccountEntity()
                .setId(80L)
                .setSubjectId(70L)
                .setScopeType(AccountScopeType.GLOBAL)
                .setStatus(AccountStatus.DISABLED)
                .setVersion(5L);
        when(accounts.require(80L)).thenReturn(account, changed);
        when(repository.hasUnreachableActivePrincipal(70L, 80L)).thenReturn(false);
        AccountLifecycleService service = new AccountLifecycleService(
                accounts, mock(SubjectService.class), repository, authentication);

        assertThat(service.changeStatus(80L, AccountStatus.DISABLED, 4L)).isSameAs(changed);

        verify(repository).updateAccountStatus(80L, AccountStatus.DISABLED, 4L);
        verify(authentication).endSessions(80L);
    }

    @Test
    void shouldDeleteSubjectWhenDeletingItsOnlyAccount() {
        AccountService accounts = mock(AccountService.class);
        SubjectService subjects = mock(SubjectService.class);
        IamRepository repository = mock(IamRepository.class);
        Authentication authentication = mock(Authentication.class);
        IamAccountEntity account = new IamAccountEntity()
                .setId(80L)
                .setSubjectId(70L)
                .setScopeType(AccountScopeType.GLOBAL)
                .setStatus(AccountStatus.ACTIVE)
                .setVersion(4L);
        IamSubjectEntity subject = new IamSubjectEntity().setId(70L).setVersion(6L);
        when(accounts.require(80L)).thenReturn(account);
        when(accounts.findBySubject(70L)).thenReturn(List.of(account));
        when(subjects.require(70L)).thenReturn(subject);
        AccountLifecycleService service = new AccountLifecycleService(accounts, subjects, repository, authentication);

        service.delete(80L, 4L);

        verify(authentication).endSessions(80L);
        verify(subjects).delete(70L, 6L);
        verify(repository, never()).isolateAccount(80L, 4L);
    }

    @Test
    void shouldIsolateOnlyDeletedAccountWhenSubjectHasAnotherAccount() {
        AccountService accounts = mock(AccountService.class);
        SubjectService subjects = mock(SubjectService.class);
        IamRepository repository = mock(IamRepository.class);
        Authentication authentication = mock(Authentication.class);
        IamAccountEntity account = new IamAccountEntity()
                .setId(80L)
                .setSubjectId(70L)
                .setScopeType(AccountScopeType.GLOBAL)
                .setStatus(AccountStatus.ACTIVE)
                .setVersion(4L);
        IamAccountEntity remainingAccount = new IamAccountEntity()
                .setId(81L)
                .setSubjectId(70L)
                .setScopeType(AccountScopeType.GLOBAL)
                .setStatus(AccountStatus.ACTIVE)
                .setVersion(2L);
        when(accounts.require(80L)).thenReturn(account);
        when(accounts.findBySubject(70L)).thenReturn(List.of(account, remainingAccount));
        AccountLifecycleService service = new AccountLifecycleService(accounts, subjects, repository, authentication);

        service.delete(80L, 4L);

        verify(authentication).endSessions(80L);
        verify(repository).isolateAccount(80L, 4L);
        verify(subjects, never()).delete(70L, 4L);
    }
}
