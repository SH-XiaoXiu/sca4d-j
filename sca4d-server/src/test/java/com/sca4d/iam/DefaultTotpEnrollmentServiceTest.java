package com.sca4d.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.sca4d.common.auth.TotpCredential;
import com.sca4d.common.concurrent.lock.DistributedLock;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.security.EncryptedSecret;
import com.sca4d.common.security.SecretKeyring;
import com.sca4d.config.properties.Sca4djAuthenticationProperties;
import com.sca4d.config.properties.Sca4djSecretKeyringProperties;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.iam.account.AccountSecondFactorAvailability;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.credential.CredentialService;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.totp.TotpCodeVerifier;
import com.sca4d.iam.totp.TotpEnrollment;
import com.sca4d.iam.totp.TotpEnrollmentState;
import com.sca4d.iam.totp.TotpQrCodeGenerator;
import com.sca4d.iam.totp.repository.TotpEnrollmentRepository;
import com.sca4d.iam.totp.repository.TotpRepository;
import com.sca4d.iam.totp.service.DefaultTotpEnrollmentService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** 验证 enrollment 单次消费顺序和 TOTP Credential 修改期间的锁内保护。 */
class DefaultTotpEnrollmentServiceTest {

    @Test
    void shouldConsumeEnrollmentBeforeBindingCredential() {
        AccountService accounts = mock(AccountService.class);
        CredentialService credentials = mock(CredentialService.class);
        AccountSecondFactorAvailability secondFactors = mock(AccountSecondFactorAvailability.class);
        AuthenticationProcessService flows = mock(AuthenticationProcessService.class);
        Authentication authentication = mock(Authentication.class);
        TotpEnrollmentRepository enrollments = mock(TotpEnrollmentRepository.class);
        TotpCodeVerifier codes = mock(TotpCodeVerifier.class);
        SecretKeyring keyring = keyring();
        EncryptedSecret encrypted = keyring.encrypt("totp.enrollment.enrollment-1.secret", "SECRET");
        TotpEnrollment enrollment = new TotpEnrollment()
                .setEnrollmentId("enrollment-1")
                .setState(TotpEnrollmentState.PENDING)
                .setVersion(0L)
                .setSubjectId(10L)
                .setAccountId(30L)
                .setBrowserHash("browser-hash")
                .setSecretKeyId(encrypted.getKeyId())
                .setSecretNonce(Base64.getEncoder().encodeToString(encrypted.getNonce()))
                .setSecretCiphertext(Base64.getEncoder().encodeToString(encrypted.getCiphertext()))
                .setExpiresAt(Instant.now().plusSeconds(300));
        when(accounts.require(30L)).thenReturn(new IamAccountEntity().setId(30L).setSubjectId(10L).setScopeType(AccountScopeType.GLOBAL).setScopeId(null)
                .setStatus(AccountStatus.ACTIVE).setSecondFactorRequired(false).setVersion(0L));
        when(enrollments.find("enrollment-1")).thenReturn(enrollment);
        when(codes.matchStep(eq("SECRET"), eq("123456"), any(Instant.class))).thenReturn(100L);
        when(enrollments.consume(enrollment, 0L)).thenReturn(true);
        when(credentials.bind(eq(30L), any(TotpCredential.class))).thenReturn(40L);
        DefaultTotpEnrollmentService service = new DefaultTotpEnrollmentService(
                accounts,
                credentials,
                secondFactors,
                flows,
                authentication,
                enrollments,
                mock(TotpRepository.class),
                codes,
                mock(TotpQrCodeGenerator.class),
                keyring,
                new Sca4djAuthenticationProperties());

        var result = service.activate(10L, 30L, "enrollment-1", "123456", "browser-hash");

        assertThat(result.getCredentialId()).isEqualTo(40L);
        var order = inOrder(enrollments, credentials, authentication);
        order.verify(enrollments).consume(enrollment, 0L);
        order.verify(credentials).bind(eq(30L), any(TotpCredential.class));
        verify(authentication, never()).endSessions(30L);
    }

    @Test
    void shouldRejectDeletingOnlySecondFactorBeforeConsumingStepUp() {
        AccountService accounts = mock(AccountService.class);
        CredentialService credentials = mock(CredentialService.class);
        AccountSecondFactorAvailability secondFactors = mock(AccountSecondFactorAvailability.class);
        AuthenticationProcessService flows = mock(AuthenticationProcessService.class);
        TotpRepository totpRepository = mock(TotpRepository.class);
        when(accounts.require(30L)).thenReturn(new IamAccountEntity().setId(30L).setSubjectId(10L).setScopeType(AccountScopeType.GLOBAL).setScopeId(null)
                .setStatus(AccountStatus.ACTIVE).setSecondFactorRequired(true).setVersion(0L));
        DefaultTotpEnrollmentService service = new DefaultTotpEnrollmentService(
                accounts,
                credentials,
                secondFactors,
                flows,
                mock(Authentication.class),
                mock(TotpEnrollmentRepository.class),
                totpRepository,
                mock(TotpCodeVerifier.class),
                mock(TotpQrCodeGenerator.class),
                keyring(),
                new Sca4djAuthenticationProperties());

        assertThatThrownBy(() -> service.delete(
                new Subject(10L, "管理员"),
                new Account(30L, 10L, AccountScopeType.GLOBAL, null, true),
                30L,
                40L,
                0L,
                "flow-1",
                "browser-hash"))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getErrorCode())
                .isEqualTo(IamErrorCode.TOTP_REMOVAL_WOULD_LOCK_ACCOUNT);
        verify(flows, never()).authorizeStepUp(any(), any(), any(), any());
        verify(totpRepository, never()).delete(any(), any(), any());
    }

    @Test
    void shouldDeleteOnlyTotpAfterAnotherSecondFactorRemainsAvailable() {
        AccountService accounts = mock(AccountService.class);
        CredentialService credentials = mock(CredentialService.class);
        AccountSecondFactorAvailability secondFactors = mock(AccountSecondFactorAvailability.class);
        AuthenticationProcessService flows = mock(AuthenticationProcessService.class);
        Authentication authentication = mock(Authentication.class);
        TotpRepository totpRepository = mock(TotpRepository.class);
        IamAccountEntity targetAccount = new IamAccountEntity().setId(30L).setSubjectId(10L).setScopeType(AccountScopeType.GLOBAL).setScopeId(null)
                .setStatus(AccountStatus.ACTIVE).setSecondFactorRequired(true).setVersion(0L);
        when(accounts.require(30L)).thenReturn(targetAccount);
        when(secondFactors.availableAfterRemovingCredential(targetAccount, 40L)).thenReturn(true);
        DefaultTotpEnrollmentService service = new DefaultTotpEnrollmentService(
                accounts,
                credentials,
                secondFactors,
                flows,
                authentication,
                mock(TotpEnrollmentRepository.class),
                totpRepository,
                mock(TotpCodeVerifier.class),
                mock(TotpQrCodeGenerator.class),
                keyring(),
                new Sca4djAuthenticationProperties());

        service.delete(
                new Subject(10L, "管理员"),
                new Account(30L, 10L, AccountScopeType.GLOBAL, null, true),
                30L,
                40L,
                2L,
                "flow-1",
                "browser-hash");

        verify(totpRepository).delete(40L, 30L, 2L);
        verify(authentication, never()).endSessions(30L);
    }

    @Test
    void shouldKeepCredentialWritesAndSessionInvalidationInTransactions() throws Exception {
        var activate = DefaultTotpEnrollmentService.class.getMethod(
                "activate", Long.class, Long.class, String.class, String.class, String.class);
        var replace = DefaultTotpEnrollmentService.class.getMethod(
                "replace",
                Long.class,
                Long.class,
                Long.class,
                Long.class,
                String.class,
                String.class,
                String.class);
        var delete = DefaultTotpEnrollmentService.class.getMethod(
                "delete",
                Subject.class,
                Account.class,
                Long.class,
                Long.class,
                Long.class,
                String.class,
                String.class);

        for (var method : List.of(activate, replace, delete)) {
            assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();
            assertThat(method.isAnnotationPresent(DistributedLock.class)).isTrue();
        }
        assertThat(activate.getAnnotation(DistributedLock.class).key())
                .isEqualTo("'subject:' + #subjectId");
        assertThat(replace.getAnnotation(DistributedLock.class).key())
                .isEqualTo("'subject:' + #subjectId");
        assertThat(delete.getAnnotation(DistributedLock.class).key())
                .isEqualTo("'subject:' + #subject.subjectId");
    }

    /** @return 使用固定测试 AES-256 密钥的 keyring */
    private SecretKeyring keyring() {
        Sca4djSecretKeyringProperties properties = new Sca4djSecretKeyringProperties();
        properties.setActiveKeyId("test");
        properties.setKeys(Map.of(
                "test", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="));
        return new SecretKeyring(properties);
    }
}
