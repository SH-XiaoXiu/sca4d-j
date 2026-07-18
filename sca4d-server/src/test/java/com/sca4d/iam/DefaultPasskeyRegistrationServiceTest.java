package com.sca4d.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.AuthenticationProcessService;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.Authentication;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.concurrent.lock.DistributedLock;
import com.sca4d.common.exception.BizException;
import com.sca4d.config.properties.Sca4djAuthenticationProperties;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.iam.account.AccountSecondFactorAvailability;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.credential.CredentialService;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.passkey.PasskeyCredential;
import com.sca4d.iam.passkey.PasskeyCredentialScheme;
import com.sca4d.iam.passkey.PasskeyRegistration;
import com.sca4d.iam.passkey.PasskeyRegistrationState;
import com.sca4d.iam.passkey.repository.PasskeyRegistrationRepository;
import com.sca4d.iam.passkey.repository.PasskeyRepository;
import com.sca4d.iam.passkey.service.DefaultPasskeyRegistrationService;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** 验证 Passkey registration 单次消费、Credential 写入和 Session 失效顺序。 */
class DefaultPasskeyRegistrationServiceTest {

    @Test
    void shouldConsumeRegistrationBeforeBindingCredential() {
        AccountService accounts = mock(AccountService.class);
        CredentialService credentials = mock(CredentialService.class);
        PasskeyCredentialScheme passkeys = mock(PasskeyCredentialScheme.class);
        PasskeyRegistrationRepository registrations = mock(PasskeyRegistrationRepository.class);
        Authentication authentication = mock(Authentication.class);
        PasskeyRegistration registration = registration();
        PasskeyCredential credential = credential();
        when(accounts.require(30L)).thenReturn(account(false));
        when(registrations.find("registration-1")).thenReturn(registration);
        when(registrations.consume(registration, 0L)).thenReturn(true);
        when(passkeys.registrationCredential("request-json", "response-json", "工作电脑"))
                .thenReturn(credential);
        when(credentials.bind(30L, credential)).thenReturn(40L);
        DefaultPasskeyRegistrationService service = service(
                accounts,
                credentials,
                passkeys,
                registrations,
                mock(PasskeyRepository.class),
                mock(AccountSecondFactorAvailability.class),
                mock(AuthenticationProcessService.class),
                authentication);

        var result = service.activate(
                10L, 30L, "registration-1", "response-json", "browser-hash");

        assertThat(result.getCredentialId()).isEqualTo(40L);
        var order = inOrder(registrations, passkeys, credentials, authentication);
        order.verify(registrations).consume(registration, 0L);
        order.verify(passkeys).registrationCredential(
                "request-json", "response-json", "工作电脑");
        order.verify(credentials).bind(30L, credential);
        order.verify(authentication).endSessions(30L);
    }

    @Test
    void shouldConsumeStepUpBeforeCreatingRegistration() {
        AccountService accounts = mock(AccountService.class);
        PasskeyCredentialScheme passkeys = mock(PasskeyCredentialScheme.class);
        AuthenticationProcessService flows = mock(AuthenticationProcessService.class);
        PasskeyRegistrationRepository registrations = mock(PasskeyRegistrationRepository.class);
        PublicKeyCredentialCreationOptions request = mock(PublicKeyCredentialCreationOptions.class);
        when(accounts.require(30L)).thenReturn(account(false));
        when(passkeys.registrationUserHandle(30L)).thenReturn(new byte[32]);
        when(passkeys.registrationOptions(any(), any(), any(Long.class))).thenReturn(request);
        when(passkeys.registrationRequestJson(request)).thenReturn("request-json");
        when(passkeys.browserRegistrationOptions("request-json"))
                .thenReturn("{\"publicKey\":{}}");
        DefaultPasskeyRegistrationService service = service(
                accounts,
                mock(CredentialService.class),
                passkeys,
                registrations,
                mock(PasskeyRepository.class),
                mock(AccountSecondFactorAvailability.class),
                flows,
                mock(Authentication.class));
        Subject subject = new Subject(10L, "管理员");
        Account authenticated = new Account(
                30L, 10L, AccountScopeType.GLOBAL, null, true);

        var result = service.create(
                subject,
                authenticated,
                30L,
                "step-up-1",
                "browser-hash",
                "工作电脑");

        assertThat(result.getRegistration().getState())
                .isEqualTo(PasskeyRegistrationState.PENDING);
        var order = inOrder(flows, registrations);
        order.verify(flows).authorizeStepUp(
                "step-up-1", "browser-hash", subject, authenticated);
        order.verify(registrations).create(any(PasskeyRegistration.class), any());
    }

    @Test
    void shouldRejectDeletingLastSecondFactorBeforeConsumingStepUp() {
        AccountService accounts = mock(AccountService.class);
        AccountSecondFactorAvailability secondFactors = mock(AccountSecondFactorAvailability.class);
        AuthenticationProcessService flows = mock(AuthenticationProcessService.class);
        PasskeyRepository passkeyRepository = mock(PasskeyRepository.class);
        IamAccountEntity target = account(true);
        when(accounts.require(30L)).thenReturn(target);
        DefaultPasskeyRegistrationService service = service(
                accounts,
                mock(CredentialService.class),
                mock(PasskeyCredentialScheme.class),
                mock(PasskeyRegistrationRepository.class),
                passkeyRepository,
                secondFactors,
                flows,
                mock(Authentication.class));

        assertThatThrownBy(() -> service.delete(
                new Subject(10L, "管理员"),
                new Account(
                        30L, 10L, AccountScopeType.GLOBAL, null, true),
                30L,
                40L,
                2L,
                "step-up-1",
                "browser-hash"))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getErrorCode())
                .isEqualTo(IamErrorCode.PASSKEY_REMOVAL_WOULD_LOCK_ACCOUNT);
        verify(flows, never()).authorizeStepUp(any(), any(), any(), any());
        verify(passkeyRepository, never()).delete(any(), any(), any());
    }

    @Test
    void shouldKeepCredentialWritesAndSessionInvalidationInTransactions() throws Exception {
        var activate = DefaultPasskeyRegistrationService.class
                .getMethod(
                        "activate",
                        Long.class,
                        Long.class,
                        String.class,
                        String.class,
                        String.class);
        assertThat(activate.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(activate.getAnnotation(DistributedLock.class).key())
                .isEqualTo("'subject:' + #subjectId");
        var delete = DefaultPasskeyRegistrationService.class
                .getMethod(
                        "delete",
                        Subject.class,
                        Account.class,
                        Long.class,
                        Long.class,
                        Long.class,
                        String.class,
                        String.class);
        assertThat(delete.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(delete.getAnnotation(DistributedLock.class).key())
                .isEqualTo("'subject:' + #subject.subjectId");
    }

    /** @return PENDING 且属于测试 Subject、Account 和浏览器的 registration */
    private PasskeyRegistration registration() {
        return new PasskeyRegistration()
                .setRegistrationId("registration-1")
                .setState(PasskeyRegistrationState.PENDING)
                .setVersion(0L)
                .setSubjectId(10L)
                .setAccountId(30L)
                .setBrowserHash("browser-hash")
                .setRequestJson("request-json")
                .setDisplayName("工作电脑")
                .setExpiresAt(Instant.now().plusSeconds(60));
    }

    /** @return 只用于验证 Service 参数传递的测试 Passkey Credential */
    private PasskeyCredential credential() {
        return new PasskeyCredential(
                new byte[32],
                new byte[] {1},
                new byte[] {2},
                0,
                new byte[16],
                false,
                false,
                "工作电脑");
    }

    /** @return 当前测试 Subject 名下的 GLOBAL Account */
    private IamAccountEntity account(boolean secondFactorRequired) {
        return new IamAccountEntity().setId(30L).setSubjectId(10L).setScopeType(AccountScopeType.GLOBAL).setScopeId(null).setStatus(AccountStatus.ACTIVE)
                .setSecondFactorRequired(secondFactorRequired).setVersion(0L);
    }

    /** @return 使用测试依赖的 Passkey Service */
    private DefaultPasskeyRegistrationService service(
            AccountService accounts,
            CredentialService credentials,
            PasskeyCredentialScheme passkeys,
            PasskeyRegistrationRepository registrations,
            PasskeyRepository passkeyRepository,
            AccountSecondFactorAvailability secondFactors,
            AuthenticationProcessService flows,
            Authentication authentication) {
        return new DefaultPasskeyRegistrationService(
                accounts,
                credentials,
                passkeys,
                registrations,
                passkeyRepository,
                secondFactors,
                flows,
                authentication,
                new Sca4djAuthenticationProperties());
    }
}
