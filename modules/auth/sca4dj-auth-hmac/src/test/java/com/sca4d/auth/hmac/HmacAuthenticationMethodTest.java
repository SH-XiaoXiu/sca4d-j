package com.sca4d.auth.hmac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.authentication.SessionControl;
import com.sca4d.core.api.authentication.SessionTerminationReason;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.security.EncryptedSecret;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/** 验证 MAC Key 轮换和撤销使用独立 Credential，并使旧 Session 失效。 */
class HmacAuthenticationMethodTest {

    private static final Account ACCOUNT = new Account(20, 10, new RealmKey("machine"));

    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");

    @Test
    @DisplayName("轮换 HMAC Key 递增版本并终止旧 Session")
    void rotatesExistingCredential() {
        HmacCredentialRepository credentials = mock(HmacCredentialRepository.class);
        HmacKeyCryptography cryptography = mock(HmacKeyCryptography.class);
        SessionControl sessions = mock(SessionControl.class);
        HmacCredential current = credential(2);
        HmacCredential rotated = credential(3);
        when(credentials.find(ACCOUNT.accountId())).thenReturn(current);
        when(cryptography.encrypt(anyLong(), anyLong(), any(String.class)))
                .thenReturn(new EncryptedSecret("key", new byte[] {1}));
        when(credentials.replace(any(HmacCredential.class), any(EncryptedSecret.class), any(), any()))
                .thenReturn(rotated);
        HmacAuthenticationMethod method = new HmacAuthenticationMethod(
                credentials,
                cryptography,
                sessions,
                new SecureRandom(new byte[] {1, 2, 3}),
                mock(HmacAuthenticationAttemptStore.class),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new HmacProperties());

        IssuedHmacKey issued = method.issue(ACCOUNT);

        assertThat(issued.credential().version()).isEqualTo(new Version(3));
        assertThat(issued.key()).isNotBlank();
        verify(sessions).terminateAccount(ACCOUNT.accountId(), SessionTerminationReason.AUTHENTICATION_CHANGED);
    }

    private static HmacCredential credential(long version) {
        return new HmacCredential(
                ACCOUNT.accountId(),
                "key",
                new byte[] {1},
                HmacCredential.Status.ACTIVE,
                new Version(version),
                NOW);
    }
}
