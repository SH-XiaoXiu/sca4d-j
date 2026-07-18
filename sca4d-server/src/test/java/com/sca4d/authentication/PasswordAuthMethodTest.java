package com.sca4d.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.password.PasswordAuthMethod;
import com.sca4d.authentication.password.PasswordAuthenticationProof;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.PasswordProof;
import com.sca4d.common.auth.Subject;
import com.sca4d.entity.IamAccountIdentifierEntity;
import com.sca4d.iam.credential.CredentialService;
import com.sca4d.iam.credential.CredentialVerification;
import com.sca4d.iam.identifier.AccountIdentifierService;
import com.sca4d.iam.identifier.AccountIdentifierStatus;
import com.sca4d.iam.identifier.AccountIdentifierType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

/** 验证 PASSWORD 方式负责 AccountIdentifier 解析和 Credential 复核。 */
class PasswordAuthMethodTest {

    @Test
    void shouldDeclareLoginAndStepUpCapabilities() {
        PasswordAuthMethod method = new PasswordAuthMethod(
                mock(AccountIdentifierService.class), mock(CredentialService.class));

        assertThat(method.capabilities())
                .containsExactlyInAnyOrder(AuthMethodCapability.LOGIN, AuthMethodCapability.STEP_UP);
    }

    @Test
    void shouldResolveIdentifierAndVerifyPasswordCredential() {
        AccountIdentifierService identifiers = mock(AccountIdentifierService.class);
        CredentialService credentials = mock(CredentialService.class);
        PasswordProof password = new PasswordProof("secret-password");
        IamAccountIdentifierEntity identifier = new IamAccountIdentifierEntity().setId(50L).setAccountId(30L).setScopeType(AccountScopeType.TENANT)
                .setScopeId(20L).setIdentifierType(AccountIdentifierType.USERNAME).setValue("admin").setContactId(null)
                .setStatus(AccountIdentifierStatus.ACTIVE).setVersion(0L);
        CredentialVerification credential = new CredentialVerification(
                new Subject(10L, "管理员"),
                new Account(30L, 10L, AccountScopeType.TENANT, 20L),
                40L,
                3L);
        when(identifiers.findActive(
                AccountScopeType.TENANT, 20L, AccountIdentifierType.USERNAME, "admin"))
                .thenReturn(identifier);
        when(credentials.verify(30L, password)).thenReturn(credential);

        AuthMethodVerification result = new PasswordAuthMethod(identifiers, credentials)
                .verify(
                        new AuthenticationProcess()
                                .setScopeType(AccountScopeType.TENANT)
                                .setScopeId(20L),
                        new PasswordAuthenticationProof(
                                AccountIdentifierType.USERNAME, "admin", password));

        assertThat(result.getMethodType()).isEqualTo(AuthMethodType.PASSWORD);
        assertThat(result.getAccount().getAccountId()).isEqualTo(30L);
        assertThat(result.getProofId()).isEqualTo("40");
        assertThat(result.getProofVersion()).isEqualTo(3L);
    }

    @Test
    void shouldUseNonexistentAccountSentinelForUnknownIdentifier() {
        AccountIdentifierService identifiers = mock(AccountIdentifierService.class);
        CredentialService credentials = mock(CredentialService.class);
        PasswordProof password = new PasswordProof("wrong-password");
        when(credentials.verify(-1L, password)).thenReturn(new CredentialVerification(
                new Subject(10L, "管理员"),
                new Account(30L, 10L, AccountScopeType.TENANT, 20L),
                40L,
                3L));

        new PasswordAuthMethod(identifiers, credentials)
                .verify(
                        new AuthenticationProcess().setScopeType(AccountScopeType.GLOBAL),
                        new PasswordAuthenticationProof(
                                AccountIdentifierType.EMAIL, "unknown@example.com", password));

        verify(credentials).verify(-1L, password);
    }

    @Test
    void shouldVerifyBoundAccountWithoutIdentifier() {
        CredentialService credentials = mock(CredentialService.class);
        PasswordProof password = new PasswordProof("secret-password");
        CredentialVerification credential = new CredentialVerification(
                new Subject(10L, "管理员"),
                new Account(30L, 10L, AccountScopeType.GLOBAL, null),
                40L,
                3L);
        when(credentials.verify(30L, password)).thenReturn(credential);

        AuthMethodVerification result = new PasswordAuthMethod(
                mock(AccountIdentifierService.class), credentials)
                .verify(
                        new AuthenticationProcess().setAccountId(30L).setScopeType(AccountScopeType.GLOBAL),
                        new PasswordAuthenticationProof(null, null, password));

        assertThat(result.getAccount().getAccountId()).isEqualTo(30L);
        verify(credentials).verify(30L, password);
    }

    @Test
    void shouldRecheckCredentialBeforeSessionCreation() {
        CredentialService credentials = mock(CredentialService.class);
        CredentialVerification credential = new CredentialVerification(
                new Subject(10L, "管理员"),
                new Account(30L, 10L, AccountScopeType.TENANT, 20L),
                40L,
                3L);
        when(credentials.requireCurrent(ArgumentMatchers.any(CredentialVerification.class)))
                .thenReturn(credential);
        AuthMethodVerification verification = new AuthMethodVerification(
                AuthMethodType.PASSWORD,
                credential.getSubject(),
                credential.getAccount(),
                credential.getCredentialId().toString(),
                credential.getCredentialVersion());

        AuthMethodVerification current = new PasswordAuthMethod(
                mock(AccountIdentifierService.class), credentials)
                .requireCurrent(verification);

        assertThat(current.getMethodType()).isEqualTo(AuthMethodType.PASSWORD);
        assertThat(current.getProofId()).isEqualTo("40");
        verify(credentials).requireCurrent(ArgumentMatchers.any(CredentialVerification.class));
    }
}
