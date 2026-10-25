package com.sca4d.auth.totp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.auth.totp.credential.TotpCodeVerifier;
import com.sca4d.auth.totp.credential.TotpCredential;
import com.sca4d.auth.totp.credential.TotpCredentialRepository;
import com.sca4d.auth.totp.enrollment.RedisTotpEnrollmentStore;
import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.VerifiedAuthentication;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.api.security.SecretCipher;
import com.sca4d.core.api.security.SecretKeyring;
import com.sca4d.core.spring.security.AesGcmSecretCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/** 验证 TOTP 方法贡献 Possession 因素、拒绝旧时间步并复核凭据版本。 */
class TotpAuthenticationMethodTest {

    private static final String SECRET = "JBSWY3DPEHPK3PXP";

    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_010L);

    @Test
    @DisplayName("TOTP 允许重复使用同一时间步，并拒绝更旧时间步")
    void acceptsTheSameDatabaseStepRepeatedlyAndRejectsAnOlderStep() {
        Account account = account();
        SecretKeyring keyring = keyring();
        EncryptedSecret encrypted = keyring.activeCipher()
                .encrypt(
                        TotpAuthenticationMethod.credentialPurpose(account.accountId()),
                        SECRET.getBytes(StandardCharsets.UTF_8));
        TotpCredential credential = new TotpCredential(account.accountId(), encrypted, 0, new Version(7));
        TotpCredentialRepository credentials = mock(TotpCredentialRepository.class);
        when(credentials.findByAccountId(account.accountId())).thenReturn(credential);
        long step = NOW.toEpochMilli() / TotpCodeVerifier.STEP_MILLIS;
        when(credentials.advanceAcceptedStep(account.accountId(), credential.version(), step))
                .thenReturn(true, true);
        when(credentials.advanceAcceptedStep(account.accountId(), credential.version(), step - 1))
                .thenReturn(false);
        TotpCodeVerifier verifier = mock(TotpCodeVerifier.class);
        when(verifier.matchStep(SECRET, "123456", NOW))
                .thenReturn(OptionalLong.of(step), OptionalLong.of(step), OptionalLong.of(step - 1));
        TotpAuthenticationMethod method = new TotpAuthenticationMethod(
                credentials,
                mock(RedisTotpEnrollmentStore.class),
                verifier,
                keyring,
                Clock.fixed(NOW, ZoneOffset.UTC));
        TotpProof proof = new TotpProof("123456");

        VerifiedAuthentication first = method.authenticate(account, proof);
        VerifiedAuthentication second = method.authenticate(account, proof);

        assertThat(first.authentication().factorKinds())
                .containsExactly(AuthenticationFactorKind.POSSESSION);
        assertThat(second.authentication().factorKinds())
                .containsExactly(AuthenticationFactorKind.POSSESSION);
        assertThat(first.version()).isEqualTo(new Version(7));
        assertThat(second.version()).isEqualTo(new Version(7));
        assertThatThrownBy(
                () -> method.authenticate(
                        account,
                        proof))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(TotpErrorCode.CODE_REJECTED);
    }

    @Test
    @DisplayName("TOTP 凭据替换后拒绝旧证据")
    void rejectsAuthenticationAfterCredentialReplacement() {
        Account account = account();
        TotpCredentialRepository credentials = mock(TotpCredentialRepository.class);
        TotpAuthenticationMethod method = new TotpAuthenticationMethod(
                credentials,
                mock(RedisTotpEnrollmentStore.class),
                new TotpCodeVerifier(),
                keyring(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        var authentication = new VerifiedAuthentication(
                account,
                new Authentication(
                        account.accountId(),
                        TotpAuthenticationMethod.CODE,
                        Set.of(AuthenticationFactorKind.POSSESSION),
                        NOW),
                Long.toString(account.accountId()),
                new Version(2),
                null);
        when(credentials.findByAccountId(account.accountId())).thenReturn(
                new TotpCredential(
                        account.accountId(),
                        new EncryptedSecret("current", new byte[] {1}),
                        0,
                        new Version(3)));

        assertThatThrownBy(() -> method.verifyCurrent(account, authentication))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TOTP 凭据已变化");
    }

    private static Account account() {
        return new Account(10, 20, new RealmKey("platform"));
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
}
