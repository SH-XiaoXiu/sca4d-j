package com.sca4d.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.totp.TotpAuthMethod;
import com.sca4d.authentication.totp.TotpAuthenticationProof;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.TotpProof;
import com.sca4d.iam.credential.CredentialService;
import com.sca4d.iam.credential.CredentialVerification;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

/** 验证 TOTP 只作为已固定 Account 的独立第二认证因素。 */
class TotpAuthMethodTest {

    @Test
    void shouldDeclareStepUpCapabilityAndVerifyFlowAccount() {
        CredentialService credentials = mock(CredentialService.class);
        CredentialVerification verification = new CredentialVerification(
                new Subject(10L, "管理员"),
                new Account(30L, 10L, AccountScopeType.GLOBAL, null, true),
                40L,
                3L);
        when(credentials.verify(
                ArgumentMatchers.eq(30L),
                ArgumentMatchers.any(TotpProof.class)))
                .thenReturn(verification);
        TotpAuthMethod method = new TotpAuthMethod(credentials);

        AuthMethodVerification result = method.verify(
                new AuthenticationProcess()
                        .setScopeType(AccountScopeType.GLOBAL)
                        .setSubjectId(10L)
                        .setAccountId(30L)
                        .setSecondFactorRequired(true),
                new TotpAuthenticationProof("123456"));

        assertThat(method.capabilities()).containsExactly(AuthMethodCapability.STEP_UP);
        assertThat(result.getMethodType()).isEqualTo(AuthMethodType.TOTP);
        assertThat(result.getProofId()).isEqualTo("40");
        verify(credentials).verify(
                ArgumentMatchers.eq(30L),
                ArgumentMatchers.any(TotpProof.class));
    }
}
