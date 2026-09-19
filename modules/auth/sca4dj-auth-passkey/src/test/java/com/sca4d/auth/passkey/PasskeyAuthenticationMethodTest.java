package com.sca4d.auth.passkey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.auth.passkey.ceremony.PasskeyAssertionService;
import com.sca4d.auth.passkey.credential.PasskeyCredential;
import com.sca4d.auth.passkey.credential.PasskeyCredentialRepository;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.AccountDirectory;
import com.sca4d.core.api.identity.RealmKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

/** 验证 Passkey 可识别账号但只向认证引擎贡献持有因素。 */
class PasskeyAuthenticationMethodTest {

    @Test
    @DisplayName("discoverable Passkey 识别账号并只贡献 POSSESSION")
    void identifiesAccountAndContributesOnlyPossession() {
        Instant now = Instant.parse("2026-07-31T00:00:00Z");
        Account account = new Account(20, 10, new RealmKey("platform"));
        PasskeyCredential credential = credential();
        PasskeyAssertionService assertions = mock(PasskeyAssertionService.class);
        PasskeyProof proof = new PasskeyProof(new PasskeyCeremonyId("ceremony"), "response");
        when(assertions.finish(null, proof)).thenReturn(credential);
        PasskeyCredentialRepository credentials = mock(PasskeyCredentialRepository.class);
        AccountDirectory accounts = mock(AccountDirectory.class);
        when(accounts.activeById(credential.accountId())).thenReturn(account);
        PasskeyAuthenticationMethod method = new PasskeyAuthenticationMethod(
                assertions,
                credentials,
                accounts,
                Clock.fixed(now, ZoneOffset.UTC));

        var verification = method.authenticate(null, proof);

        assertThat(verification.account()).isEqualTo(account);
        assertThat(verification.authentication().factorKinds())
                .containsExactly(AuthenticationFactorKind.POSSESSION);
        assertThat(method.factorKinds()).containsExactly(AuthenticationFactorKind.POSSESSION);
        assertThat(method.canIdentifyAccount()).isTrue();
    }

    private static PasskeyCredential credential() {
        return new PasskeyCredential(
                1,
                20,
                new byte[] {1},
                new byte[] {2},
                new byte[] {3},
                0,
                Set.of("internal"),
                true,
                true,
                "Laptop",
                Instant.parse("2026-07-30T00:00:00Z"),
                null,
                new Version(0));
    }
}
