package com.sca4d.iam;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.AuthenticationProcessService;
import com.sca4d.authentication.SessionService;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.Authentication;
import com.sca4d.common.auth.Subject;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.entity.IamContactEntity;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.account.service.ManagedAccountProfileService;
import com.sca4d.iam.contact.ContactService;
import com.sca4d.iam.contact.ContactType;
import com.sca4d.iam.profile.service.DefaultProfileService;
import com.sca4d.iam.repository.IamRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

/** 验证平台代管资料修改后的身份确认和会话一致性。 */
class ManagedAccountProfileServiceTest {

    @Test
    void shouldRefreshSubjectSessionsWhenEmailDoesNotChange() {
        AccountService accounts = mock(AccountService.class);
        ContactService contacts = mock(ContactService.class);
        IamRepository repository = mock(IamRepository.class);
        AuthenticationProcessService authentications = mock(AuthenticationProcessService.class);
        Authentication authentication = mock(Authentication.class);
        SessionService sessions = mock(SessionService.class);
        IamAccountEntity target = new IamAccountEntity()
                .setId(80L)
                .setSubjectId(70L)
                .setScopeType(AccountScopeType.GLOBAL)
                .setStatus(AccountStatus.ACTIVE)
                .setVersion(4L);
        IamContactEntity currentEmail = new IamContactEntity()
                .setSubjectId(70L)
                .setContactType(ContactType.EMAIL)
                .setValue("Admin@Example.test");
        Subject operatorSubject = new Subject(8L, "管理员");
        Account operatorAccount = new Account(7L, 8L, AccountScopeType.GLOBAL, null, false);
        when(accounts.require(80L)).thenReturn(target);
        when(contacts.current(70L, ContactType.EMAIL)).thenReturn(currentEmail);
        ManagedAccountProfileService service = new ManagedAccountProfileService(
                accounts,
                mock(DefaultProfileService.class),
                contacts,
                repository,
                authentications,
                authentication,
                sessions);

        service.update(
                operatorSubject,
                operatorAccount,
                80L,
                70L,
                "新名称",
                "13800000000",
                " admin@example.test ",
                3L,
                "step-up-1",
                "browser-hash");

        verify(authentications).authorizeStepUp(
                "step-up-1", "browser-hash", operatorSubject, operatorAccount);
        verify(repository).updateSubject(70L, "新名称", 3L);
        verify(contacts).replace(70L, ContactType.PHONE, "13800000000");
        verify(contacts).replace(70L, ContactType.EMAIL, " admin@example.test ");
        verify(sessions).refreshAfterCommit(List.of(70L));
        verify(authentication, never()).endSessions(80L);
    }

    @Test
    void shouldEndEverySubjectAccountSessionWhenEmailChanges() {
        AccountService accounts = mock(AccountService.class);
        ContactService contacts = mock(ContactService.class);
        Authentication authentication = mock(Authentication.class);
        IamAccountEntity target = new IamAccountEntity()
                .setId(80L)
                .setSubjectId(70L)
                .setScopeType(AccountScopeType.GLOBAL)
                .setStatus(AccountStatus.ACTIVE)
                .setVersion(4L);
        IamAccountEntity secondAccount = new IamAccountEntity()
                .setId(81L)
                .setSubjectId(70L)
                .setScopeType(AccountScopeType.TENANT)
                .setScopeId(20L)
                .setStatus(AccountStatus.ACTIVE)
                .setVersion(2L);
        IamContactEntity currentEmail = new IamContactEntity()
                .setSubjectId(70L)
                .setContactType(ContactType.EMAIL)
                .setValue("old@example.test");
        Subject operatorSubject = new Subject(8L, "管理员");
        Account operatorAccount = new Account(7L, 8L, AccountScopeType.GLOBAL, null, false);
        when(accounts.require(80L)).thenReturn(target);
        when(accounts.findBySubject(70L)).thenReturn(List.of(target, secondAccount));
        when(contacts.current(70L, ContactType.EMAIL)).thenReturn(currentEmail);
        ManagedAccountProfileService service = new ManagedAccountProfileService(
                accounts,
                mock(DefaultProfileService.class),
                contacts,
                mock(IamRepository.class),
                mock(AuthenticationProcessService.class),
                authentication,
                mock(SessionService.class));

        service.update(
                operatorSubject,
                operatorAccount,
                80L,
                70L,
                "管理员",
                null,
                "new@example.test",
                3L,
                "step-up-1",
                "browser-hash");

        verify(authentication).endSessions(80L);
        verify(authentication).endSessions(81L);
    }
}
