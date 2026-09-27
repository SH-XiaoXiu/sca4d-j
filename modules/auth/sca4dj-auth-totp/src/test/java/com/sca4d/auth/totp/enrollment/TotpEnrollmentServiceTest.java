package com.sca4d.auth.totp.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.auth.totp.TotpAuthenticationMethod;
import com.sca4d.auth.totp.config.TotpSettings;
import com.sca4d.auth.totp.credential.TotpCodeVerifier;
import com.sca4d.auth.totp.credential.TotpCredential;
import com.sca4d.auth.totp.credential.TotpCredentialRepository;
import com.sca4d.core.api.authentication.SessionControl;
import com.sca4d.core.api.authentication.SessionTerminationReason;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.api.security.SecretCipher;
import com.sca4d.core.api.security.SecretKeyring;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.access.SettingWriter;
import com.sca4d.core.api.setting.value.SettingOwner;
import com.sca4d.core.api.setting.value.SettingValue;
import com.sca4d.core.spring.security.AesGcmSecretCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.OptionalLong;

/** 验证 enrollment 密钥用途、激活时间步保存和敏感变更 Session 效果。 */
class TotpEnrollmentServiceTest {

    private static final long ACCOUNT_ID = 10L;

    private static final Instant NOW = Instant.parse("2026-07-26T06:00:00Z");

    private static final String SECRET = "JBSWY3DPEHPK3PXP";

    @Test
    @DisplayName("创建加密 enrollment 并返回临时配置 URI")
    void createsEncryptedEnrollmentAndReturnsTransientProvisioningUri() {
        Fixture fixture = fixture();
        when(fixture.verifier.generateSecret()).thenReturn(SECRET);
        ArgumentCaptor<TotpEnrollmentState> state = ArgumentCaptor.forClass(TotpEnrollmentState.class);

        TotpEnrollment enrollment = fixture.service.create(ACCOUNT_ID);
        verify(fixture.enrollments).create(state.capture());
        when(fixture.enrollments.loadById(enrollment.enrollmentId())).thenReturn(state.getValue());

        String uri = fixture.service.provisioningUri(
                ACCOUNT_ID,
                enrollment.enrollmentId(),
                "SCA4D Dev",
                "admin@example.com");

        assertThat(enrollment.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(new String(state.getValue().secret().ciphertext(), StandardCharsets.UTF_8))
                .doesNotContain(SECRET);
        assertThat(uri)
                .startsWith("otpauth://totp/SCA4D%20Dev:admin%40example.com?")
                .contains("secret=" + SECRET)
                .contains("algorithm=SHA1&digits=6&period=30");
    }

    @Test
    @DisplayName("保存激活时间步并重加密凭据，不终止当前 Session")
    void storesActivationStepReencryptsForCredentialWithoutTerminatingSessions() {
        Fixture fixture = fixture();
        TotpEnrollmentState enrollment = enrollment(fixture.keyring, "enrollment-1");
        when(fixture.enrollments.loadById("enrollment-1")).thenReturn(enrollment);
        when(fixture.verifier.matchStep(SECRET, "123456", NOW)).thenReturn(OptionalLong.of(100));
        when(fixture.credentials.create(any(), any(), any(Long.class))).thenAnswer(
                invocation -> new TotpCredential(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        new Version(0)));

        TotpCredential credential = fixture.service.activate(ACCOUNT_ID, "enrollment-1", "123456");

        verify(fixture.enrollments).consume(enrollment);
        verify(fixture.sessions, never()).terminateAccount(ACCOUNT_ID, SessionTerminationReason.AUTHENTICATION_CHANGED);
        assertThat(credential.lastAcceptedStep()).isEqualTo(100);
        assertThat(
                new String(
                        fixture.keyring.activeCipher()
                                .decrypt(TotpAuthenticationMethod.credentialPurpose(ACCOUNT_ID), credential.secret()),
                        StandardCharsets.UTF_8))
                .isEqualTo(SECRET);
    }

    @Test
    @DisplayName("替换和删除 TOTP 凭据时终止账号的旧 Session")
    void replacementAndDeletionTerminateAllOldAccountSessions() {
        Fixture fixture = fixture();
        TotpEnrollmentState enrollment = enrollment(fixture.keyring, "enrollment-2");
        when(fixture.enrollments.loadById("enrollment-2")).thenReturn(enrollment);
        when(fixture.verifier.matchStep(SECRET, "654321", NOW)).thenReturn(OptionalLong.of(101));
        when(fixture.credentials.findByAccountId(ACCOUNT_ID)).thenReturn(
                new TotpCredential(
                        ACCOUNT_ID,
                        fixture.keyring.activeCipher()
                                .encrypt(
                                        TotpAuthenticationMethod.credentialPurpose(ACCOUNT_ID),
                                        SECRET.getBytes(StandardCharsets.UTF_8)),
                        100,
                        new Version(3)),
                new TotpCredential(
                        ACCOUNT_ID,
                        fixture.keyring.activeCipher()
                                .encrypt(
                                        TotpAuthenticationMethod.credentialPurpose(ACCOUNT_ID),
                                        SECRET.getBytes(StandardCharsets.UTF_8)),
                        101,
                        new Version(4)));
        when(fixture.credentials.replace(any(), any(), anyLong(), any())).thenAnswer(
                invocation -> new TotpCredential(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        new Version(4)));

        fixture.service.replace(ACCOUNT_ID, "enrollment-2", "654321");
        fixture.service.delete(ACCOUNT_ID);

        verify(fixture.enrollments).consume(enrollment);
        verify(fixture.credentials).delete(ACCOUNT_ID, new Version(4));
        verify(fixture.sessions, Mockito.times(2))
                .terminateAccount(ACCOUNT_ID, SessionTerminationReason.AUTHENTICATION_CHANGED);
    }

    @Test
    @DisplayName("只读取和取消属于当前账号的待确认 enrollment")
    void readsAndCancelsOnlyAnOwnedPendingEnrollment() {
        Fixture fixture = fixture();
        TotpEnrollmentState enrollment = enrollment(fixture.keyring, "enrollment-3");
        when(fixture.enrollments.loadById("enrollment-3")).thenReturn(enrollment);

        assertThat(fixture.service.loadById(ACCOUNT_ID, "enrollment-3").enrollmentId())
                .isEqualTo("enrollment-3");
        fixture.service.cancel(ACCOUNT_ID, "enrollment-3");

        verify(fixture.enrollments).delete(enrollment);
    }

    private static TotpEnrollmentState enrollment(SecretKeyring keyring, String id) {
        EncryptedSecret encrypted = keyring.activeCipher()
                .encrypt(
                        "totp.enrollment." + id + ".secret",
                        SECRET.getBytes(StandardCharsets.UTF_8));
        return new TotpEnrollmentState(
                id,
                ACCOUNT_ID,
                encrypted,
                NOW.plus(Duration.ofMinutes(10)),
                TotpEnrollmentStatus.PENDING,
                0);
    }

    private static Fixture fixture() {
        RedisTotpEnrollmentStore enrollments = mock(RedisTotpEnrollmentStore.class);
        TotpCredentialRepository credentials = mock(TotpCredentialRepository.class);
        TotpCodeVerifier verifier = mock(TotpCodeVerifier.class);
        SessionControl sessions = mock(SessionControl.class);
        SecretKeyring keyring = keyring();
        TestAccess source = mock(TestAccess.class);
        TotpSettings settings = new TotpSettings(source, source);
        when(source.read(settings.ENROLLMENT_TTL.code(), SettingOwner.system())).thenReturn(
                SettingValue.read(
                        TreeCode.of("SETTING:TEST"),
                        settings.ENROLLMENT_TTL.code(),
                        SettingOwner.system(),
                        settings.ENROLLMENT_TTL.codec().encode(Duration.ofMinutes(10)),
                        1,
                        new Version(0),
                        false));
        TotpEnrollmentService service = new TotpEnrollmentService(
                enrollments,
                credentials,
                verifier,
                keyring,
                sessions,
                settings,
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(service, enrollments, credentials, verifier, sessions, keyring);
    }

    private static SecretKeyring keyring() {
        SecretCipher cipher = new AesGcmSecretCipher("current", new byte[32]);
        return new SecretKeyring() {
            @Override
            public SecretCipher activeCipher() {
                return cipher;
            }

            @Override
            public Optional<SecretCipher> findCipher(String keyId) {
                return cipher.keyId().equals(keyId) ? Optional.of(cipher) : Optional.empty();
            }
        };
    }

    @SuppressWarnings("ClassCanBeRecord")
    private static final class Fixture {
        private final TotpEnrollmentService service;

        private final RedisTotpEnrollmentStore enrollments;

        private final TotpCredentialRepository credentials;

        private final TotpCodeVerifier verifier;

        private final SessionControl sessions;

        private final SecretKeyring keyring;

        private Fixture(
                TotpEnrollmentService service,
                RedisTotpEnrollmentStore enrollments,
                TotpCredentialRepository credentials,
                TotpCodeVerifier verifier,
                SessionControl sessions,
                SecretKeyring keyring) {
            this.service = service;
            this.enrollments = enrollments;
            this.credentials = credentials;
            this.verifier = verifier;
            this.sessions = sessions;
            this.keyring = keyring;
        }
    }

    /** 测试替身同时实现读取和写入契约，避免引入生产组合接口。 */
    private interface TestAccess extends SettingReader<String>, SettingWriter<String> {
    }
}
