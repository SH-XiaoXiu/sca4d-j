package com.sca4d.auth.sharedsecret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.SessionControl;
import com.sca4d.core.api.authentication.VerifiedAuthentication;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.security.SecretHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

/** 验证 Shared Secret 只认证已确定的 Account，并用凭据版本使旧证据失效。 */
class SharedSecretAuthenticationMethodTest {

    private static final Account ACCOUNT = new Account(20, 10, new RealmKey("machine"));

    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");

    @Test
    @DisplayName("有效 Secret 返回 Account 级 possession 证据")
    void authenticatesResolvedAccount() {
        SharedSecretCredentialRepository credentials = mock(SharedSecretCredentialRepository.class);
        SecretHasher hasher = mock(SecretHasher.class);
        SharedSecretCredential credential = credential("ACTIVE", 3);
        when(credentials.find(ACCOUNT.accountId())).thenReturn(credential);
        when(hasher.matches("secret", "digest")).thenReturn(true);
        SharedSecretAuthenticationMethod method = new SharedSecretAuthenticationMethod(
                credentials,
                hasher,
                mock(SessionControl.class),
                new SecureRandom(new byte[] {1}),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var verification = method.authenticate(
                ACCOUNT,
                new SharedSecretProof("secret"));

        assertThat(verification.account()).isSameAs(ACCOUNT);
        assertThat(verification.authentication().factorKinds())
                .containsExactly(AuthenticationFactorKind.POSSESSION);
        assertThat(verification.version()).isEqualTo(new Version(3));
    }

    @Test
    @DisplayName("凭据撤销后拒绝旧认证证据")
    void rejectsAuthenticationAfterRevocation() {
        SharedSecretCredentialRepository credentials = mock(SharedSecretCredentialRepository.class);
        when(credentials.find(ACCOUNT.accountId())).thenReturn(credential("REVOKED", 4));
        SharedSecretAuthenticationMethod method = new SharedSecretAuthenticationMethod(
                credentials,
                mock(SecretHasher.class),
                mock(SessionControl.class),
                new SecureRandom(new byte[] {1}),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(
                () -> method.verifyCurrent(
                        ACCOUNT,
                        verification(new Version(3))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Shared Secret 凭据已变化");
    }

    private static VerifiedAuthentication verification(Version version) {
        return new VerifiedAuthentication(
                ACCOUNT,
                new Authentication(
                        ACCOUNT.accountId(),
                        SharedSecretAuthenticationMethod.CODE,
                        Set.of(AuthenticationFactorKind.POSSESSION),
                        NOW),
                Long.toString(ACCOUNT.accountId()),
                version,
                null);
    }

    private static SharedSecretCredential credential(String status, long version) {
        return new SharedSecretCredential(
                ACCOUNT.accountId(),
                "digest",
                SharedSecretCredential.Status.valueOf(status),
                new Version(version),
                NOW);
    }
}
