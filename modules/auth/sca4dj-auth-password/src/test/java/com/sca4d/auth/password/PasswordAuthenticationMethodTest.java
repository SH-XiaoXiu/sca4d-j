package com.sca4d.auth.password;

import static com.sca4d.core.api.authentication.SessionTerminationReason.AUTHENTICATION_CHANGED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.auth.password.credential.PasswordCredential;
import com.sca4d.auth.password.credential.PasswordCredentialRepository;
import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.SessionControl;
import com.sca4d.core.api.authentication.VerifiedAuthentication;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.AccountDirectory;
import com.sca4d.core.api.identity.AccountIdentifierType;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.security.SecretHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 验证 Password 认证方法的账号发现、凭据版本校验、状态变更和禁止恢复停用凭据的行为。 */
class PasswordAuthenticationMethodTest {

    private static final Account ACCOUNT = new Account(
            20,
            10,
            new RealmKey("platform"));

    private static final RealmKey ACCOUNT_REALM = new RealmKey("platform");

    private static final Instant NOW = Instant.parse("2026-07-26T00:00:00Z");

    @Test
    @DisplayName("Discovers Account And Returns Versioned Knowledge Authentication")
    void discoversAccountAndReturnsVersionedKnowledgeAuthentication() {
        AccountDirectory accounts = mock(AccountDirectory.class);
        PasswordCredentialRepository credentials = mock(PasswordCredentialRepository.class);
        SecretHasher hasher = mock(SecretHasher.class);
        PasswordCredential credential = new PasswordCredential(
                ACCOUNT.accountId(),
                "hash",
                Instant.EPOCH,
                new Version(4));
        when(accounts.resolve(ACCOUNT_REALM, AccountIdentifierType.USERNAME, "admin"))
                .thenReturn(Optional.of(ACCOUNT));
        when(credentials.findByAccountId(ACCOUNT.accountId())).thenReturn(credential);
        when(hasher.matches("secret", "hash")).thenReturn(true);
        PasswordAuthenticationMethod method = new PasswordAuthenticationMethod(
                accounts,
                ACCOUNT_REALM,
                List.of(AccountIdentifierType.USERNAME),
                credentials,
                hasher,
                mock(SessionControl.class),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var verification = method.authenticate(
                null,
                new PasswordProof("admin", "secret"));

        assertThat(verification.account()).isSameAs(ACCOUNT);
        assertThat(verification.authentication().factorKinds())
                .containsExactly(AuthenticationFactorKind.KNOWLEDGE);
        assertThat(verification.version()).isEqualTo(new Version(4));
        method.verifyCurrent(ACCOUNT, verification);
    }

    @Test
    @DisplayName("Rejects Authentication After Credential Version Changes")
    void rejectsAuthenticationAfterCredentialVersionChanges() {
        AccountDirectory accounts = mock(AccountDirectory.class);
        PasswordCredentialRepository credentials = mock(PasswordCredentialRepository.class);
        SecretHasher hasher = mock(SecretHasher.class);
        when(credentials.findByAccountId(ACCOUNT.accountId()))
                .thenReturn(
                        new PasswordCredential(
                                ACCOUNT.accountId(),
                                "hash",
                                Instant.EPOCH,
                                new Version(5)));
        PasswordAuthenticationMethod method = new PasswordAuthenticationMethod(
                accounts,
                ACCOUNT_REALM,
                List.of(AccountIdentifierType.USERNAME),
                credentials,
                hasher,
                mock(SessionControl.class),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(
                () -> method.verifyCurrent(
                        ACCOUNT,
                        verification(new Version(4))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Password 凭据已变化");
    }

    @Test
    @DisplayName("Discovers Account By Configured Email Identifier")
    void discoversAccountByConfiguredEmailIdentifier() {
        AccountIdentifierType email = new AccountIdentifierType("EMAIL");
        AccountDirectory accounts = mock(AccountDirectory.class);
        PasswordCredentialRepository credentials = mock(PasswordCredentialRepository.class);
        SecretHasher hasher = mock(SecretHasher.class);
        when(accounts.resolve(ACCOUNT_REALM, AccountIdentifierType.USERNAME, "admin@example.com"))
                .thenReturn(Optional.empty());
        when(accounts.resolve(ACCOUNT_REALM, email, "admin@example.com"))
                .thenReturn(Optional.of(ACCOUNT));
        when(credentials.findByAccountId(ACCOUNT.accountId())).thenReturn(
                new PasswordCredential(ACCOUNT.accountId(), "hash", Instant.EPOCH, new Version(1)));
        when(hasher.matches("secret", "hash")).thenReturn(true);
        PasswordAuthenticationMethod method = new PasswordAuthenticationMethod(
                accounts,
                ACCOUNT_REALM,
                List.of(AccountIdentifierType.USERNAME, email),
                credentials,
                hasher,
                mock(SessionControl.class),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var verification = method.authenticate(
                null,
                new PasswordProof("admin@example.com", "secret"));

        assertThat(verification.account()).isSameAs(ACCOUNT);
    }

    @Test
    @DisplayName("Rejects Identifier That Resolves To Different Accounts")
    void rejectsIdentifierThatResolvesToDifferentAccounts() {
        AccountIdentifierType email = new AccountIdentifierType("EMAIL");
        Account other = new Account(21, 11, ACCOUNT_REALM);
        AccountDirectory accounts = mock(AccountDirectory.class);
        PasswordAuthenticationMethod method = new PasswordAuthenticationMethod(
                accounts,
                ACCOUNT_REALM,
                List.of(AccountIdentifierType.USERNAME, email),
                mock(PasswordCredentialRepository.class),
                mock(SecretHasher.class),
                mock(SessionControl.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(accounts.resolve(ACCOUNT_REALM, AccountIdentifierType.USERNAME, "shared"))
                .thenReturn(Optional.of(ACCOUNT));
        when(accounts.resolve(ACCOUNT_REALM, email, "shared"))
                .thenReturn(Optional.of(other));

        assertThatThrownBy(
                () -> method.authenticate(
                        null,
                        new PasswordProof("shared", "secret")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Rejects Authentication When Password Reset Is Required")
    void rejectsAuthenticationWhenPasswordResetIsRequired() {
        AccountDirectory accounts = mock(AccountDirectory.class);
        PasswordCredentialRepository credentials = mock(PasswordCredentialRepository.class);
        SecretHasher hasher = mock(SecretHasher.class);
        when(accounts.resolve(ACCOUNT_REALM, AccountIdentifierType.USERNAME, "admin"))
                .thenReturn(Optional.of(ACCOUNT));
        when(credentials.findByAccountId(ACCOUNT.accountId())).thenReturn(
                new PasswordCredential(
                        ACCOUNT.accountId(),
                        "hash",
                        Instant.EPOCH,
                        new Version(1),
                        PasswordCredential.Status.RESET_REQUIRED));
        when(hasher.matches("secret", "hash")).thenReturn(true);
        PasswordAuthenticationMethod method = newMethod(
                accounts,
                credentials,
                hasher,
                mock(SessionControl.class));

        assertThatThrownBy(() -> method.authenticate(null, new PasswordProof("admin", "secret")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Changes Password Status And Terminates Existing Sessions")
    void changesPasswordStatusAndTerminatesExistingSessions() {
        AccountDirectory accounts = mock(AccountDirectory.class);
        PasswordCredentialRepository credentials = mock(PasswordCredentialRepository.class);
        SessionControl sessions = mock(SessionControl.class);
        PasswordCredential current = new PasswordCredential(
                ACCOUNT.accountId(),
                "hash",
                Instant.EPOCH,
                new Version(1));
        PasswordCredential changed = new PasswordCredential(
                ACCOUNT.accountId(),
                "hash",
                Instant.EPOCH,
                new Version(2),
                PasswordCredential.Status.RESET_REQUIRED);
        when(
                credentials.findByAccountId(
                        ACCOUNT.accountId(),
                        PasswordCredential.Status.ACTIVE))
                .thenReturn(current);
        when(credentials.changeStatus(current, PasswordCredential.Status.RESET_REQUIRED))
                .thenReturn(changed);
        PasswordAuthenticationMethod method = newMethod(accounts, credentials, mock(SecretHasher.class), sessions);

        assertThat(
                method.changeStatus(
                        ACCOUNT.accountId(),
                        PasswordCredential.Status.ACTIVE,
                        PasswordCredential.Status.RESET_REQUIRED))
                .isSameAs(changed);
        verify(sessions).terminateAccount(
                ACCOUNT.accountId(),
                AUTHENTICATION_CHANGED);
    }

    @Test
    @DisplayName("Does Not Restore Disabled Password Credential")
    void doesNotRestoreDisabledPasswordCredential() {
        PasswordAuthenticationMethod method = newMethod(
                mock(AccountDirectory.class),
                mock(PasswordCredentialRepository.class),
                mock(SecretHasher.class),
                mock(SessionControl.class));

        assertThatThrownBy(
                () -> method.replace(
                        ACCOUNT.accountId(),
                        "secret",
                        PasswordCredential.Status.DISABLED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DISABLED");
    }

    private static PasswordAuthenticationMethod newMethod(
            AccountDirectory accounts,
            PasswordCredentialRepository credentials,
            SecretHasher hasher,
            SessionControl sessions) {
        return new PasswordAuthenticationMethod(
                accounts,
                ACCOUNT_REALM,
                List.of(AccountIdentifierType.USERNAME),
                credentials,
                hasher,
                sessions,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static VerifiedAuthentication verification(Version version) {
        return new VerifiedAuthentication(
                ACCOUNT,
                new Authentication(
                        ACCOUNT.accountId(),
                        PasswordAuthenticationMethod.CODE,
                        Set.of(AuthenticationFactorKind.KNOWLEDGE),
                        NOW),
                Long.toString(ACCOUNT.accountId()),
                version,
                null);
    }
}
