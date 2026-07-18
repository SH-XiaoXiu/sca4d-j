package com.sca4d.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.passkey.PasskeyAuthMethod;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.Subject;
import com.sca4d.iam.credential.CredentialService;
import com.sca4d.iam.credential.CredentialVerification;
import com.sca4d.iam.passkey.PasskeyCredentialScheme;
import com.sca4d.iam.passkey.PasskeyProof;
import org.junit.jupiter.api.Test;

/** 验证 Passkey 声明 LOGIN/STEP_UP，并返回 Credential ID 和版本。 */
class PasskeyAuthMethodTest {

    @Test
    void shouldVerifyPasskeyWithinRequestedAccountScope() {
        PasskeyCredentialScheme passkeys = mock(PasskeyCredentialScheme.class);
        PasskeyProof proof = new PasskeyProof("request", "response");
        CredentialVerification verification = new CredentialVerification(
                new Subject(10L, "管理员"),
                new Account(30L, 10L, AccountScopeType.GLOBAL, null, true),
                40L,
                3L);
        when(passkeys.verify(AccountScopeType.GLOBAL, null, proof)).thenReturn(verification);
        PasskeyAuthMethod method = new PasskeyAuthMethod(passkeys, mock(CredentialService.class));

        AuthMethodVerification result = method.verify(
                new AuthenticationProcess().setScopeType(AccountScopeType.GLOBAL), proof);

        assertThat(method.capabilities())
                .containsExactlyInAnyOrder(AuthMethodCapability.LOGIN, AuthMethodCapability.STEP_UP);
        assertThat(result.getMethodType()).isEqualTo(AuthMethodType.PASSKEY);
        assertThat(result.getProofId()).isEqualTo("40");
        assertThat(result.getProofVersion()).isEqualTo(3L);
    }
}
