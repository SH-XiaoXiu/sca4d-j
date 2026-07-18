package com.sca4d.authentication.method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.AuthMethodCapability;
import com.sca4d.authentication.AuthMethodType;
import com.sca4d.authentication.AuthMethodVerification;
import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.authentication.email.EmailCodeAuthMethod;
import com.sca4d.authentication.email.EmailCodeAuthenticationProof;
import com.sca4d.authentication.email.EmailCodeChallenge;
import com.sca4d.authentication.email.EmailCodeChallengeService;
import com.sca4d.authentication.email.EmailCodeChallengeState;
import com.sca4d.authentication.email.codec.EmailCodeChallengeReference;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.Subject;
import org.junit.jupiter.api.Test;

/** 验证邮件验证码方式的 Challenge 归属、Proof 引用和 Session 前复核。 */
class EmailCodeAuthMethodTest {

    @Test
    void shouldConsumeChallengeAndReturnVersionedProofReference() {
        EmailCodeChallengeService challenges = mock(EmailCodeChallengeService.class);
        Subject subject = new Subject(10L, "Alice");
        Account account = new Account(20L, 10L, AccountScopeType.TENANT, 30L, true);
        EmailCodeAuthenticationProof proof = new EmailCodeAuthenticationProof(
                "authentication-1", "challenge-1", "123456");
        when(challenges.consume("authentication-1", "challenge-1", "123456"))
                .thenReturn(new EmailCodeChallenge()
                        .setAuthenticationId("authentication-1")
                        .setChallengeId("challenge-1")
                        .setSubjectId(10L)
                        .setAccountId(20L)
                        .setScopeType(AccountScopeType.TENANT)
                        .setScopeId(30L)
                        .setState(EmailCodeChallengeState.CONSUMED)
                        .setVersion(2L));

        AuthMethodVerification verification = new EmailCodeAuthMethod(challenges).verify(
                new AuthenticationProcess()
                        .setAuthenticationId("authentication-1")
                        .setScopeType(AccountScopeType.TENANT)
                        .setScopeId(30L)
                        .setSubjectId(subject.getSubjectId())
                        .setAccountId(account.getAccountId())
                        .setSecondFactorRequired(true),
                proof);

        assertThat(verification.getMethodType()).isEqualTo(AuthMethodType.EMAIL_CODE);
        assertThat(verification.getProofId()).isEqualTo("authentication-1/challenge-1");
        assertThat(verification.getProofVersion()).isEqualTo(2L);
        assertThat(new EmailCodeAuthMethod(challenges).capabilities())
                .containsExactly(AuthMethodCapability.STEP_UP);
    }

    @Test
    void shouldRecheckConsumedChallengeFromOpaqueReference() {
        EmailCodeChallengeService challenges = mock(EmailCodeChallengeService.class);
        AuthMethodVerification verification = new AuthMethodVerification(
                AuthMethodType.EMAIL_CODE,
                new Subject(10L, "Alice"),
                new Account(20L, 10L, AccountScopeType.GLOBAL, null, true),
                new EmailCodeChallengeReference("flow-1", "challenge-1").value(),
                2L);

        new EmailCodeAuthMethod(challenges).requireCurrent(verification);

        verify(challenges).requireConsumed("flow-1", "challenge-1", 2L);
    }
}
