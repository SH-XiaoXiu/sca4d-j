package com.sca4d.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.CredentialType;
import com.sca4d.common.auth.PasswordCredential;
import com.sca4d.common.auth.PasswordProof;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.UnauthenticatedException;
import com.sca4d.common.concurrent.lock.DistributedLockExecutor;
import com.sca4d.common.concurrent.lock.DistributedLockTemplate;
import com.sca4d.common.concurrent.lock.LockedOperation;
import com.sca4d.common.exception.BizException;
import com.sca4d.config.properties.Sca4djConcurrentProperties;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.entity.IamSubjectEntity;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.credential.CredentialScheme;
import com.sca4d.iam.credential.CredentialVerification;
import com.sca4d.iam.credential.service.DefaultCredentialService;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.repository.IamRepository;
import com.sca4d.iam.subject.SubjectLock;
import com.sca4d.iam.subject.SubjectStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

/** 验证认证材料按 Java 类型选择 Scheme，并执行绑定、替换和状态修改。 */
class DefaultCredentialServiceTest {

    @Test
    void shouldBindCredentialInsideSharedSubjectLock() throws Throwable {
        DistributedLockExecutor executor = mock(DistributedLockExecutor.class);
        when(executor.execute(anyString(), any(), any(), any())).thenAnswer(invocation -> ((LockedOperation<?>) invocation.getArgument(3)).execute());
        Sca4djConcurrentProperties properties = new Sca4djConcurrentProperties();
        SubjectLock subjectLock = new SubjectLock(new DistributedLockTemplate(executor, properties), properties);
        @SuppressWarnings("unchecked")
        CredentialScheme<PasswordCredential, PasswordProof> scheme = mock(CredentialScheme.class);
        PasswordCredential credential = new PasswordCredential("secret");
        when(scheme.credentialClass()).thenReturn(PasswordCredential.class);
        when(scheme.proofClass()).thenReturn(PasswordProof.class);
        when(scheme.credentialType()).thenReturn(CredentialType.PASSWORD);
        when(scheme.bind(30L, credential)).thenReturn(40L);
        IamRepository repository = mock(IamRepository.class);
        AccountService accountService = mock(AccountService.class);
        when(accountService.require(30L)).thenReturn(new IamAccountEntity().setId(30L).setSubjectId(10L).setScopeType(AccountScopeType.GLOBAL).setScopeId(null)
                .setStatus(AccountStatus.ACTIVE).setSecondFactorRequired(true).setVersion(0L));
        DefaultCredentialService service = new DefaultCredentialService(
                List.of(scheme), subjectLock, accountService, repository);

        assertThat(service.bind(30L, credential)).isEqualTo(40L);

        verify(executor).execute(eq("lock:subject:10"), any(), any(), any());
        verify(accountService, times(2)).require(30L);
        verify(scheme).bind(30L, credential);
    }

    @Test
    void shouldRejectBindingWhenAccountWasDeletedBeforeSubjectLock() throws Throwable {
        DistributedLockExecutor executor = mock(DistributedLockExecutor.class);
        when(executor.execute(anyString(), any(), any(), any())).thenAnswer(invocation -> ((LockedOperation<?>) invocation.getArgument(3)).execute());
        Sca4djConcurrentProperties properties = new Sca4djConcurrentProperties();
        SubjectLock subjectLock = new SubjectLock(new DistributedLockTemplate(executor, properties), properties);
        @SuppressWarnings("unchecked")
        CredentialScheme<PasswordCredential, PasswordProof> scheme = mock(CredentialScheme.class);
        PasswordCredential credential = new PasswordCredential("secret");
        when(scheme.credentialClass()).thenReturn(PasswordCredential.class);
        when(scheme.proofClass()).thenReturn(PasswordProof.class);
        when(scheme.credentialType()).thenReturn(CredentialType.PASSWORD);
        AccountService accountService = mock(AccountService.class);
        when(accountService.require(30L))
                .thenReturn(new IamAccountEntity().setId(30L).setSubjectId(10L).setScopeType(AccountScopeType.GLOBAL).setScopeId(null)
                        .setStatus(AccountStatus.ACTIVE).setSecondFactorRequired(true).setVersion(0L))
                .thenThrow(new BizException(IamErrorCode.ACCOUNT_NOT_FOUND));
        DefaultCredentialService service = new DefaultCredentialService(
                List.of(scheme), subjectLock, accountService, mock(IamRepository.class));

        assertThatThrownBy(() -> service.bind(30L, credential))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getErrorCode())
                .isEqualTo(IamErrorCode.ACCOUNT_NOT_FOUND);
        verify(scheme, never()).bind(any(), any());
    }

    @Test
    void shouldRejectCredentialVerificationWhenVersionIsNoLongerCurrent() {
        SubjectLock subjectLock = mock(SubjectLock.class);
        IamRepository repository = mock(IamRepository.class);
        CredentialVerification verification = new CredentialVerification(
                new Subject(10L, "管理员"),
                new Account(30L, 10L, AccountScopeType.GLOBAL, null),
                20L,
                3L);
        DefaultCredentialService service = new DefaultCredentialService(
                List.of(), subjectLock, mock(AccountService.class), repository);
        when(repository.isCredentialCurrent(20L, 30L, 3L)).thenReturn(false);

        assertThatThrownBy(() -> service.requireCurrent(verification))
                .isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void shouldReloadCurrentSubjectAfterCredentialVersionCheck() {
        SubjectLock subjectLock = mock(SubjectLock.class);
        IamRepository repository = mock(IamRepository.class);
        CredentialVerification verification = new CredentialVerification(
                new Subject(10L, "旧名称"),
                new Account(30L, 10L, AccountScopeType.GLOBAL, null),
                20L,
                3L);
        when(repository.isCredentialCurrent(20L, 30L, 3L)).thenReturn(true);
        when(repository.findAccount(30L)).thenReturn(new IamAccountEntity().setId(30L).setSubjectId(10L).setScopeType(AccountScopeType.GLOBAL).setScopeId(null)
                .setStatus(AccountStatus.ACTIVE).setSecondFactorRequired(true).setVersion(0L));
        when(repository.findSubject(10L)).thenReturn(new IamSubjectEntity().setId(10L).setDisplayName("当前名称").setStatus(SubjectStatus.ACTIVE).setVersion(4L));
        DefaultCredentialService service = new DefaultCredentialService(
                List.of(), subjectLock, mock(AccountService.class), repository);

        CredentialVerification current = service.requireCurrent(verification);

        assertThat(current.getSubject().getSubjectId()).isEqualTo(10L);
        assertThat(current.getSubject().getDisplayName()).isEqualTo("当前名称");
        assertThat(current.getAccount().getAccountId()).isEqualTo(30L);
        assertThat(current.getAccount().isSecondFactorRequired()).isTrue();
    }

}
