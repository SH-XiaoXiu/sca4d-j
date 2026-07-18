package com.sca4d.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.AuthenticationProcessService;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.Authentication;
import com.sca4d.common.auth.CredentialType;
import com.sca4d.common.auth.SessionSnapshot;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.concurrent.lock.DistributedLock;
import com.sca4d.common.exception.BizException;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.entity.IamAccountIdentifierEntity;
import com.sca4d.entity.IamCredentialEntity;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.account.service.DefaultAccountSecurityService;
import com.sca4d.iam.credential.CredentialService;
import com.sca4d.iam.credential.CredentialStatus;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.identifier.AccountIdentifierService;
import com.sca4d.iam.identifier.AccountIdentifierStatus;
import com.sca4d.iam.identifier.AccountIdentifierType;
import com.sca4d.iam.passkey.PasskeySecurityDetails;
import com.sca4d.iam.passkey.repository.PasskeyRepository;
import com.sca4d.iam.recovery.IssuedRecoveryCodes;
import com.sca4d.iam.recovery.RecoveryCodeService;
import com.sca4d.iam.recovery.RecoveryCodeSummary;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 验证账号安全查询、Account 归属隔离和 STEP_UP 撤销顺序。 */
class DefaultAccountSecurityServiceTest {

    @Test
    void shouldAssembleAccountsWithFixedBatchReads() {
        AccountService accountService = mock(AccountService.class);
        AccountIdentifierService identifierService = mock(AccountIdentifierService.class);
        CredentialService credentialService = mock(CredentialService.class);
        IamAccountEntity global = account(30L, 10L, AccountScopeType.GLOBAL, null);
        IamAccountEntity tenant = account(31L, 10L, AccountScopeType.TENANT, 20L);
        IamAccountIdentifierEntity identifier = identifier(101L, 30L);
        IamCredentialEntity credential = new IamCredentialEntity()
                .setId(201L)
                .setAccountId(31L)
                .setCredentialType(CredentialType.PASSWORD)
                .setStatus(CredentialStatus.ACTIVE)
                .setCreatedAt(OffsetDateTime.parse("2026-07-17T08:00:00+08:00"))
                .setUpdatedAt(OffsetDateTime.parse("2026-07-17T08:00:00+08:00"))
                .setVersion(0L);
        when(accountService.findBySubject(10L)).thenReturn(List.of(global, tenant));
        when(identifierService.findByAccounts(List.of(30L, 31L)))
                .thenReturn(Map.of(30L, List.of(identifier), 31L, List.of()));
        when(credentialService.findBySubjects(List.of(10L)))
                .thenReturn(Map.of(10L, List.of(credential)));
        RecoveryCodeService recoveryCodeService = mock(RecoveryCodeService.class);
        PasskeyRepository passkeyRepository = mock(PasskeyRepository.class);
        PasskeySecurityDetails passkey = new PasskeySecurityDetails(
                202L,
                31L,
                CredentialStatus.ACTIVE,
                OffsetDateTime.parse("2026-07-17T08:10:00+08:00"),
                OffsetDateTime.parse("2026-07-17T08:20:00+08:00"),
                2L,
                "工作电脑",
                true,
                true,
                OffsetDateTime.parse("2026-07-17T08:20:00+08:00"));
        RecoveryCodeSummary recoveryCodes = new RecoveryCodeSummary(
                301L, 31L, OffsetDateTime.parse("2026-07-17T08:30:00+08:00"), 8, 0L);
        when(recoveryCodeService.findByAccounts(List.of(30L, 31L)))
                .thenReturn(Map.of(31L, recoveryCodes));
        when(passkeyRepository.findSecurityByAccounts(List.of(30L, 31L)))
                .thenReturn(Map.of(31L, List.of(passkey)));
        DefaultAccountSecurityService service = service(
                accountService,
                identifierService,
                credentialService,
                recoveryCodeService,
                passkeyRepository,
                mock(AuthenticationProcessService.class),
                mock(Authentication.class));

        var result = service.accounts(10L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAccount()).isSameAs(global);
        assertThat(result.get(0).getIdentifiers()).containsExactly(identifier);
        assertThat(result.get(0).getCredentials()).isEmpty();
        assertThat(result.get(1).getAccount()).isSameAs(tenant);
        assertThat(result.get(1).getIdentifiers()).isEmpty();
        assertThat(result.get(1).getCredentials()).containsExactly(credential);
        assertThat(result.get(1).getPasskeyCredentials()).containsExactly(passkey);
        assertThat(result.get(1).getRecoveryCodeSummary()).isSameAs(recoveryCodes);
        verify(accountService).findBySubject(10L);
        verify(identifierService).findByAccounts(List.of(30L, 31L));
        verify(credentialService).findBySubjects(List.of(10L));
        verify(recoveryCodeService).findByAccounts(List.of(30L, 31L));
        verify(passkeyRepository).findSecurityByAccounts(List.of(30L, 31L));
    }

    @Test
    void shouldReturnAccountsWithoutCredentials() {
        AccountService accountService = mock(AccountService.class);
        AccountIdentifierService identifierService = mock(AccountIdentifierService.class);
        CredentialService credentialService = mock(CredentialService.class);
        when(accountService.findBySubject(10L))
                .thenReturn(List.of(account(30L, 10L, AccountScopeType.GLOBAL, null)));
        when(identifierService.findByAccounts(List.of(30L))).thenReturn(Map.of(30L, List.of()));
        when(credentialService.findBySubjects(List.of(10L))).thenReturn(Map.of());
        RecoveryCodeService recoveryCodeService = mock(RecoveryCodeService.class);
        when(recoveryCodeService.findByAccounts(List.of(30L))).thenReturn(Map.of());
        DefaultAccountSecurityService service = service(
                accountService,
                identifierService,
                credentialService,
                recoveryCodeService,
                mock(PasskeyRepository.class),
                mock(AuthenticationProcessService.class),
                mock(Authentication.class));

        assertThat(service.accounts(10L).getFirst().getCredentials()).isEmpty();
    }

    @Test
    void shouldReadSessionsOnlyAfterConfirmingAccountOwnership() {
        AccountService accountService = mock(AccountService.class);
        Authentication authentication = mock(Authentication.class);
        SessionSnapshot session = new SessionSnapshot(
                "session-1", 30L, "browser-1", "Chrome", Instant.parse("2026-07-17T00:00:00Z"));
        when(accountService.require(30L))
                .thenReturn(account(30L, 10L, AccountScopeType.GLOBAL, null));
        when(authentication.sessions(30L)).thenReturn(List.of(session));
        DefaultAccountSecurityService service = service(
                accountService,
                mock(AccountIdentifierService.class),
                mock(CredentialService.class),
                mock(RecoveryCodeService.class),
                mock(PasskeyRepository.class),
                mock(AuthenticationProcessService.class),
                authentication);

        assertThat(service.sessions(10L, 30L)).containsExactly(session);
        InOrder operations = inOrder(accountService, authentication);
        operations.verify(accountService).require(30L);
        operations.verify(authentication).sessions(30L);
    }

    @Test
    void shouldHideAccountOwnedByAnotherSubject() {
        AccountService accountService = mock(AccountService.class);
        AuthenticationProcessService flowService = mock(AuthenticationProcessService.class);
        Authentication authentication = mock(Authentication.class);
        when(accountService.require(30L))
                .thenReturn(account(30L, 11L, AccountScopeType.GLOBAL, null));
        DefaultAccountSecurityService service = service(
                accountService,
                mock(AccountIdentifierService.class),
                mock(CredentialService.class),
                mock(RecoveryCodeService.class),
                mock(PasskeyRepository.class),
                flowService,
                authentication);

        assertThatThrownBy(() -> service.sessions(10L, 30L))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(IamErrorCode.ACCOUNT_NOT_FOUND));
        verifyNoInteractions(flowService, authentication);
    }

    @Test
    void shouldConsumeStepUpBeforeEndingTargetSession() {
        AccountService accountService = mock(AccountService.class);
        AuthenticationProcessService flowService = mock(AuthenticationProcessService.class);
        Authentication authentication = mock(Authentication.class);
        Subject subject = new Subject(10L, "管理员");
        Account authenticatedAccount = new Account(
                30L, 10L, AccountScopeType.GLOBAL, null, true);
        when(accountService.require(31L))
                .thenReturn(account(31L, 10L, AccountScopeType.TENANT, 20L));
        DefaultAccountSecurityService service = service(
                accountService,
                mock(AccountIdentifierService.class),
                mock(CredentialService.class),
                mock(RecoveryCodeService.class),
                mock(PasskeyRepository.class),
                flowService,
                authentication);

        service.endSession(subject, authenticatedAccount, 31L, "session-1", "flow-1", "browser-hash");

        InOrder operations = inOrder(accountService, flowService, authentication);
        operations.verify(accountService).require(31L);
        operations.verify(flowService)
                .authorizeStepUp("flow-1", "browser-hash", subject, authenticatedAccount);
        operations.verify(authentication).endSession(31L, "session-1");
    }

    @Test
    void shouldConsumeStepUpIssueRecoveryCodesAndEndAllSessionsWhenEnablingSecondFactor() {
        AccountService accountService = mock(AccountService.class);
        RecoveryCodeService recoveryCodeService = mock(RecoveryCodeService.class);
        AuthenticationProcessService flowService = mock(AuthenticationProcessService.class);
        Authentication authentication = mock(Authentication.class);
        Subject subject = new Subject(10L, "管理员");
        Account authenticatedAccount = new Account(
                30L, 10L, AccountScopeType.GLOBAL, null, false);
        IamAccountEntity target = account(31L, 10L, AccountScopeType.TENANT, 20L);
        IamAccountEntity updated = new IamAccountEntity().setId(31L).setSubjectId(10L).setScopeType(AccountScopeType.TENANT).setScopeId(20L)
                .setStatus(AccountStatus.ACTIVE).setSecondFactorRequired(true).setVersion(1L);
        RecoveryCodeSummary summary = new RecoveryCodeSummary(
                301L, 31L, OffsetDateTime.parse("2026-07-17T09:00:00+08:00"), 10, 0L);
        when(accountService.require(31L)).thenReturn(target);
        when(accountService.changeSecondFactorRequirement(31L, true, 0L)).thenReturn(updated);
        when(recoveryCodeService.issue(31L))
                .thenReturn(new IssuedRecoveryCodes(summary, List.of("AAAAA-BBBBB-CCCCC-DDDDD")));
        DefaultAccountSecurityService service = service(
                accountService,
                mock(AccountIdentifierService.class),
                mock(CredentialService.class),
                recoveryCodeService,
                mock(PasskeyRepository.class),
                flowService,
                authentication);

        var result = service.changeSecondFactorRequirement(
                subject, authenticatedAccount, 31L, true, 0L, "flow-1", "browser-hash");

        assertThat(result.getAccount()).isSameAs(updated);
        assertThat(result.getRecoveryCodeSummary()).isSameAs(summary);
        assertThat(result.getRecoveryCodes()).containsExactly("AAAAA-BBBBB-CCCCC-DDDDD");
        InOrder operations = inOrder(accountService, flowService, recoveryCodeService, authentication);
        operations.verify(accountService).require(31L);
        operations.verify(flowService)
                .authorizeStepUp("flow-1", "browser-hash", subject, authenticatedAccount);
        operations.verify(recoveryCodeService).issue(31L);
        operations.verify(accountService).changeSecondFactorRequirement(31L, true, 0L);
        verify(authentication, never()).endSessions(31L);
    }

    @Test
    void shouldReissueRecoveryCodesOnlyForMfaEnabledAccountAndEndTargetSessions() {
        AccountService accountService = mock(AccountService.class);
        RecoveryCodeService recoveryCodeService = mock(RecoveryCodeService.class);
        AuthenticationProcessService flowService = mock(AuthenticationProcessService.class);
        Authentication authentication = mock(Authentication.class);
        Subject subject = new Subject(10L, "管理员");
        Account authenticatedAccount = new Account(30L, 10L, AccountScopeType.GLOBAL, null, false);
        IamAccountEntity target = account(31L, 11L, AccountScopeType.TENANT, 20L)
                .setSecondFactorRequired(true)
                .setVersion(4L);
        IamAccountEntity updated = account(31L, 11L, AccountScopeType.TENANT, 20L)
                .setSecondFactorRequired(true)
                .setVersion(5L);
        RecoveryCodeSummary summary = new RecoveryCodeSummary(
                301L, 31L, OffsetDateTime.parse("2026-07-18T09:00:00+08:00"), 10, 0L);
        when(accountService.require(31L)).thenReturn(target);
        when(recoveryCodeService.issue(31L))
                .thenReturn(new IssuedRecoveryCodes(summary, List.of("AAAAA-BBBBB-CCCCC-DDDDD")));
        when(accountService.changeSecondFactorRequirement(31L, true, 4L)).thenReturn(updated);
        DefaultAccountSecurityService service = service(
                accountService,
                mock(AccountIdentifierService.class),
                mock(CredentialService.class),
                recoveryCodeService,
                mock(PasskeyRepository.class),
                flowService,
                authentication);

        var result = service.reissueManagedRecoveryCodes(
                subject, authenticatedAccount, 31L, 4L, "flow-1", "browser-hash");

        assertThat(result.getRecoveryCodes()).containsExactly("AAAAA-BBBBB-CCCCC-DDDDD");
        InOrder operations = inOrder(accountService, flowService, recoveryCodeService, authentication);
        operations.verify(accountService).require(31L);
        operations.verify(flowService).authorizeStepUp("flow-1", "browser-hash", subject, authenticatedAccount);
        operations.verify(recoveryCodeService).issue(31L);
        operations.verify(accountService).changeSecondFactorRequirement(31L, true, 4L);
        verify(authentication, never()).endSessions(31L);
    }

    @Test
    void shouldRejectRecoveryCodeReissueForAccountWithoutMfaBeforeStepUp() {
        AccountService accountService = mock(AccountService.class);
        AuthenticationProcessService flowService = mock(AuthenticationProcessService.class);
        RecoveryCodeService recoveryCodeService = mock(RecoveryCodeService.class);
        when(accountService.require(31L)).thenReturn(account(31L, 11L, AccountScopeType.TENANT, 20L));
        DefaultAccountSecurityService service = service(
                accountService,
                mock(AccountIdentifierService.class),
                mock(CredentialService.class),
                recoveryCodeService,
                mock(PasskeyRepository.class),
                flowService,
                mock(Authentication.class));

        assertThatThrownBy(() -> service.reissueManagedRecoveryCodes(
                new Subject(10L, "管理员"),
                new Account(30L, 10L, AccountScopeType.GLOBAL, null, false),
                31L,
                0L,
                "flow-1",
                "browser-hash"))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(IamErrorCode.ACCOUNT_RECOVERY_CODES_UNAVAILABLE));
        verifyNoInteractions(flowService, recoveryCodeService);
    }

    @Test
    void shouldHoldSubjectLockOutsideSecondFactorTransaction() throws Exception {
        var method = DefaultAccountSecurityService.class.getMethod(
                "changeSecondFactorRequirement",
                Subject.class,
                Account.class,
                Long.class,
                boolean.class,
                long.class,
                String.class,
                String.class);

        assertThat(method.getAnnotation(DistributedLock.class).key())
                .isEqualTo("'subject:' + #subject.subjectId");
        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void shouldRevokeRecoveryCodesBeforeEndingSessionsWhenDisablingSecondFactor() {
        AccountService accountService = mock(AccountService.class);
        RecoveryCodeService recoveryCodeService = mock(RecoveryCodeService.class);
        AuthenticationProcessService flowService = mock(AuthenticationProcessService.class);
        Authentication authentication = mock(Authentication.class);
        Subject subject = new Subject(10L, "管理员");
        Account authenticatedAccount = new Account(
                30L, 10L, AccountScopeType.GLOBAL, null, true);
        IamAccountEntity target = new IamAccountEntity().setId(31L).setSubjectId(10L).setScopeType(AccountScopeType.TENANT).setScopeId(20L)
                .setStatus(AccountStatus.ACTIVE).setSecondFactorRequired(true).setVersion(4L);
        IamAccountEntity updated = new IamAccountEntity().setId(31L).setSubjectId(10L).setScopeType(AccountScopeType.TENANT).setScopeId(20L)
                .setStatus(AccountStatus.ACTIVE).setSecondFactorRequired(false).setVersion(5L);
        when(accountService.require(31L)).thenReturn(target);
        when(accountService.changeSecondFactorRequirement(31L, false, 4L)).thenReturn(updated);
        DefaultAccountSecurityService service = service(
                accountService,
                mock(AccountIdentifierService.class),
                mock(CredentialService.class),
                recoveryCodeService,
                mock(PasskeyRepository.class),
                flowService,
                authentication);

        var result = service.changeSecondFactorRequirement(
                subject, authenticatedAccount, 31L, false, 4L, "flow-2", "browser-hash");

        assertThat(result.getAccount()).isSameAs(updated);
        assertThat(result.getRecoveryCodeSummary()).isNull();
        assertThat(result.getRecoveryCodes()).isEmpty();
        InOrder operations = inOrder(flowService, recoveryCodeService, accountService, authentication);
        operations.verify(flowService)
                .authorizeStepUp("flow-2", "browser-hash", subject, authenticatedAccount);
        operations.verify(recoveryCodeService).revoke(31L);
        operations.verify(accountService).changeSecondFactorRequirement(31L, false, 4L);
        verify(authentication, never()).endSessions(31L);
    }

    /** 创建仅包含当前测试依赖的账号安全 Service。 */
    private static DefaultAccountSecurityService service(
            AccountService accountService,
            AccountIdentifierService identifierService,
            CredentialService credentialService,
            RecoveryCodeService recoveryCodeService,
            PasskeyRepository passkeyRepository,
            AuthenticationProcessService flowService,
            Authentication authentication) {
        return new DefaultAccountSecurityService(
                accountService,
                identifierService,
                credentialService,
                recoveryCodeService,
                passkeyRepository,
                flowService,
                authentication);
    }

    /** 创建指定 Subject 和作用域下的 ACTIVE Account。 */
    private static IamAccountEntity account(
            long accountId, long subjectId, AccountScopeType scopeType, Long scopeId) {
        return new IamAccountEntity().setId(accountId).setSubjectId(subjectId).setScopeType(scopeType).setScopeId(scopeId).setStatus(AccountStatus.ACTIVE)
                .setSecondFactorRequired(false).setVersion(0L);
    }

    /** 创建属于指定 Account 的 ACTIVE USERNAME Identifier。 */
    private static IamAccountIdentifierEntity identifier(long identifierId, long accountId) {
        return new IamAccountIdentifierEntity().setId(identifierId).setAccountId(accountId).setScopeType(AccountScopeType.GLOBAL).setScopeId(null)
                .setIdentifierType(AccountIdentifierType.USERNAME).setValue("alice").setContactId(null).setStatus(AccountIdentifierStatus.ACTIVE)
                .setVersion(0L);
    }
}
