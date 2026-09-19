package com.sca4d.auth.x509;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.SessionControl;
import com.sca4d.core.api.authentication.VerifiedAuthentication;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.AccountDirectory;
import com.sca4d.core.api.identity.RealmKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/** 验证 X.509 认证证据始终绑定 Account、Credential 版本和证书有效状态。 */
class X509AuthenticationMethodTest {

    private static final Account ACCOUNT = new Account(20, 10, new RealmKey("machine"));

    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");

    @Test
    @DisplayName("证书撤销后拒绝旧认证证据")
    void rejectsAuthenticationAfterCertificateRevocation() {
        X509CredentialRepository credentials = mock(X509CredentialRepository.class);
        when(credentials.find(30)).thenReturn(
                new X509Credential(
                        30,
                        ACCOUNT.accountId(),
                        "a".repeat(64),
                        "01",
                        "CN=machine",
                        NOW.minusSeconds(60),
                        NOW.plusSeconds(3600),
                        X509Credential.Status.REVOKED,
                        new Version(2),
                        NOW));
        X509AuthenticationMethod method = new X509AuthenticationMethod(
                credentials,
                mock(AccountDirectory.class),
                mock(SessionControl.class),
                mock(IdGenerator.class),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(
                () -> method.verifyCurrent(
                        ACCOUNT,
                        new VerifiedAuthentication(
                                ACCOUNT,
                                new Authentication(
                                        ACCOUNT.accountId(),
                                        X509AuthenticationMethod.CODE,
                                        java.util.Set.of(AuthenticationFactorKind.POSSESSION),
                                        NOW),
                                "30",
                                new Version(1),
                                null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("X.509 Credential 已变化");
    }
}
