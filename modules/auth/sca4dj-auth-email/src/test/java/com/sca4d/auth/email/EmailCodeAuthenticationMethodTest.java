package com.sca4d.auth.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.auth.email.challenge.ConsumedEmailCodeChallenge;
import com.sca4d.auth.email.challenge.EmailCodeChallengeService;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.RealmKey;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/** 验证 Email Code 方法自己声明 EMAIL 类型并返回持有因素证据。 */
class EmailCodeAuthenticationMethodTest {

    @Test
    void identifiesAccountFromConsumedChallenge() {
        EmailCodeChallengeService challenges = mock(EmailCodeChallengeService.class);
        Account account = new Account(42, 21, new RealmKey("platform"));
        EmailCodeChallengeId challengeId = new EmailCodeChallengeId(UUID.randomUUID().toString());
        EmailCodeProof proof = new EmailCodeProof(challengeId, "123456");
        when(challenges.consume(proof))
                .thenReturn(new ConsumedEmailCodeChallenge(challengeId, account, new Version(1)));
        EmailCodeAuthenticationMethod method = new EmailCodeAuthenticationMethod(
                challenges,
                Clock.fixed(Instant.parse("2026-08-07T00:00:00Z"), ZoneOffset.UTC));

        var verification = method.authenticate(null, proof);

        assertThat(EmailCodeAuthenticationMethod.EMAIL_IDENTIFIER_TYPE.toString()).isEqualTo("EMAIL");
        assertThat(verification.account()).isEqualTo(account);
        assertThat(verification.authentication().factorKinds()).containsExactly(AuthenticationFactorKind.POSSESSION);
        assertThat(verification.reference()).isEqualTo(challengeId.value());
    }
}
