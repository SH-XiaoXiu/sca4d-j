package com.sca4d.authentication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.AuthenticationProcessService;
import com.sca4d.authentication.error.AuthenticationErrorCode;
import com.sca4d.authentication.recovery.ConsumedAccountRecovery;
import com.sca4d.authentication.recovery.service.DefaultAccountRecoveryService;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.Authentication;
import com.sca4d.common.auth.CredentialType;
import com.sca4d.common.auth.PasswordCredential;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.concurrent.lock.DistributedLockExecutor;
import com.sca4d.common.concurrent.lock.DistributedLockTemplate;
import com.sca4d.common.concurrent.lock.LockedOperation;
import com.sca4d.common.exception.BizException;
import com.sca4d.config.properties.Sca4djConcurrentProperties;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.entity.IamCredentialEntity;
import com.sca4d.entity.IamSubjectEntity;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.credential.CredentialService;
import com.sca4d.iam.credential.CredentialStatus;
import com.sca4d.iam.subject.SubjectLock;
import com.sca4d.iam.subject.SubjectService;
import com.sca4d.iam.subject.SubjectStatus;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** 验证账号恢复只替换目标 Account 密码，并在成功后结束该 Account 的全部登录会话。 */
class DefaultAccountRecoveryServiceTest {

    @Test
    void shouldConsumeFlowReplacePasswordAndEndAccountSessions() throws Throwable {
        AuthenticationProcessService flowService = mock(AuthenticationProcessService.class);
        AccountService accountService = mock(AccountService.class);
        SubjectService subjectService = mock(SubjectService.class);
        CredentialService credentialService = mock(CredentialService.class);
        Authentication authentication = mock(Authentication.class);
        DistributedLockExecutor lockExecutor = mock(DistributedLockExecutor.class);
        TransactionOperations transactions = mock(TransactionOperations.class);
        AtomicBoolean subjectLocked = new AtomicBoolean();
        when(lockExecutor.execute(anyString(), any(), any(), any())).thenAnswer(invocation -> {
            subjectLocked.set(true);
            try {
                return ((LockedOperation<?>) invocation.getArgument(3)).execute();
            } finally {
                subjectLocked.set(false);
            }
        });
        when(transactions.execute(any())).thenAnswer(invocation -> {
            assertThat(subjectLocked).isTrue();
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        Sca4djConcurrentProperties concurrent = new Sca4djConcurrentProperties();
        SubjectLock subjectLock = new SubjectLock(
                new DistributedLockTemplate(lockExecutor, concurrent), concurrent);
        Subject subject = new Subject(10L, "管理员");
        Account account = new Account(30L, 10L, AccountScopeType.GLOBAL, null, true);
        when(flowService.consumeRecovery("flow-1", "browser-hash"))
                .thenReturn(new ConsumedAccountRecovery(subject, account));
        when(accountService.require(30L)).thenReturn(new IamAccountEntity().setId(30L).setSubjectId(10L).setScopeType(AccountScopeType.GLOBAL).setScopeId(null)
                .setStatus(AccountStatus.ACTIVE).setSecondFactorRequired(true).setVersion(4L));
        when(subjectService.require(10L)).thenReturn(new IamSubjectEntity().setId(10L).setDisplayName("管理员").setStatus(SubjectStatus.ACTIVE).setVersion(2L));
        IamCredentialEntity credential = new IamCredentialEntity()
                .setId(40L)
                .setAccountId(30L)
                .setCredentialType(CredentialType.PASSWORD)
                .setStatus(CredentialStatus.ACTIVE)
                .setVersion(3L);
        when(credentialService.findBySubject(10L)).thenReturn(List.of(credential));
        DefaultAccountRecoveryService service = new DefaultAccountRecoveryService(
                flowService,
                subjectLock,
                subjectService,
                accountService,
                credentialService,
                authentication,
                transactions);
        PasswordCredential password = new PasswordCredential("new-password");

        service.replacePassword("flow-1", "browser-hash", password);

        InOrder order = inOrder(flowService, lockExecutor, transactions, credentialService, authentication);
        order.verify(flowService).consumeRecovery("flow-1", "browser-hash");
        order.verify(lockExecutor).execute(eq("lock:subject:10"), any(), any(), any());
        order.verify(transactions).execute(any());
        order.verify(credentialService).replace(30L, 40L, 3L, password);
        order.verify(authentication).endSessions(30L);
    }

    @Test
    void shouldRejectAccountDisabledAfterRecoveryFlowWasConsumed() throws Throwable {
        AuthenticationProcessService flowService = mock(AuthenticationProcessService.class);
        AccountService accountService = mock(AccountService.class);
        CredentialService credentialService = mock(CredentialService.class);
        Authentication authentication = mock(Authentication.class);
        TransactionOperations transactions = mock(TransactionOperations.class);
        DistributedLockExecutor lockExecutor = mock(DistributedLockExecutor.class);
        when(lockExecutor.execute(anyString(), any(), any(), any()))
                .thenAnswer(invocation -> ((LockedOperation<?>) invocation.getArgument(3)).execute());
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        Sca4djConcurrentProperties concurrent = new Sca4djConcurrentProperties();
        SubjectLock subjectLock = new SubjectLock(
                new DistributedLockTemplate(lockExecutor, concurrent), concurrent);
        when(flowService.consumeRecovery("flow-1", "browser-hash"))
                .thenReturn(new ConsumedAccountRecovery(
                        new Subject(10L, "管理员"),
                        new Account(30L, 10L, AccountScopeType.GLOBAL, null, true)));
        when(accountService.require(30L)).thenReturn(new IamAccountEntity().setId(30L).setSubjectId(10L).setScopeType(AccountScopeType.GLOBAL).setScopeId(null)
                .setStatus(AccountStatus.DISABLED).setSecondFactorRequired(true).setVersion(5L));
        SubjectService subjectService = mock(SubjectService.class);
        when(subjectService.require(10L)).thenReturn(new IamSubjectEntity().setId(10L).setDisplayName("管理员").setStatus(SubjectStatus.ACTIVE).setVersion(2L));
        DefaultAccountRecoveryService service = new DefaultAccountRecoveryService(
                flowService,
                subjectLock,
                subjectService,
                accountService,
                credentialService,
                authentication,
                transactions);

        assertThatThrownBy(() -> service.replacePassword(
                "flow-1", "browser-hash", new PasswordCredential("new-password")))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(AuthenticationErrorCode.AUTHENTICATION_CHANGED));
        verify(credentialService, never()).replace(any(), any(), any(), any());
        verify(authentication, never()).endSessions(any());
    }
}
