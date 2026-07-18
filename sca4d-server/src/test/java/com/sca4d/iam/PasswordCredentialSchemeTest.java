package com.sca4d.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.PasswordCredential;
import com.sca4d.common.auth.PasswordProof;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.credential.CredentialStatus;
import com.sca4d.iam.credential.CredentialVerification;
import com.sca4d.iam.password.PasswordCredentialScheme;
import com.sca4d.iam.password.repository.PasswordRepository;
import com.sca4d.iam.password.repository.PasswordRow;
import com.sca4d.iam.subject.SubjectStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/** 验证 PASSWORD Credential 使用 Argon2id 并返回指定 Account 的认证结果。 */
class PasswordCredentialSchemeTest {

    @Test
    void shouldHashBoundPasswordWithArgon2id() {
        PasswordRepository repository = mock(PasswordRepository.class);
        when(repository.save(ArgumentMatchers.eq(30L), startsWith("$argon2id$")))
                .thenReturn(40L);
        PasswordCredentialScheme scheme = new PasswordCredentialScheme(repository);

        assertThat(scheme.bind(30L, new PasswordCredential("secret-password"))).isEqualTo(40L);

        verify(repository).save(ArgumentMatchers.eq(30L), startsWith("$argon2id$"));
    }

    @Test
    void shouldVerifyPasswordAgainstResolvedAccount() {
        PasswordRepository repository = mock(PasswordRepository.class);
        Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
        when(repository.find(30L)).thenReturn(new PasswordRow()
                .setCredentialId(40L)
                .setAccountId(30L)
                .setSubjectId(10L)
                .setScopeType(AccountScopeType.TENANT)
                .setScopeId(20L)
                .setDisplayName("Alice")
                .setPasswordHash(encoder.encode("secret-password"))
                .setCredentialStatus(CredentialStatus.ACTIVE)
                .setAccountStatus(AccountStatus.ACTIVE)
                .setSecondFactorRequired(true)
                .setSubjectStatus(SubjectStatus.ACTIVE)
                .setCredentialVersion(3L));
        PasswordCredentialScheme scheme = new PasswordCredentialScheme(repository);

        CredentialVerification verification = scheme.verify(30L, new PasswordProof("secret-password"));

        assertThat(verification.getSubject().getSubjectId()).isEqualTo(10L);
        assertThat(verification.getAccount().getAccountId()).isEqualTo(30L);
        assertThat(verification.getAccount().getScopeType()).isEqualTo(AccountScopeType.TENANT);
        assertThat(verification.getAccount().getScopeId()).isEqualTo(20L);
        assertThat(verification.getAccount().isSecondFactorRequired()).isTrue();
        assertThat(verification.getCredentialId()).isEqualTo(40L);
        assertThat(verification.getCredentialVersion()).isEqualTo(3L);
    }
}
