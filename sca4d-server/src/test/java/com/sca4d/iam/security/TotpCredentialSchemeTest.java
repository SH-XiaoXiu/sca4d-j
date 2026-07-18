package com.sca4d.iam.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.error.AuthenticationErrorCode;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.TotpCredential;
import com.sca4d.common.auth.TotpProof;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.common.security.EncryptedSecret;
import com.sca4d.common.security.SecretKeyring;
import com.sca4d.config.properties.Sca4djSecretKeyringProperties;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.credential.CredentialStatus;
import com.sca4d.iam.subject.SubjectStatus;
import com.sca4d.iam.totp.TotpCodeVerifier;
import com.sca4d.iam.totp.TotpCredentialScheme;
import com.sca4d.iam.totp.repository.TotpRepository;
import com.sca4d.iam.totp.repository.TotpRow;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;

/** 验证 TOTP 密钥加密、认证状态和数据库原子防重放结果。 */
class TotpCredentialSchemeTest {

    @Test
    void shouldEncryptBoundSecretWithCredentialPurpose() {
        TotpRepository repository = mock(TotpRepository.class);
        IdGenerator ids = mock(IdGenerator.class);
        when(ids.nextId()).thenReturn(40L);
        SecretKeyring keyring = keyring();
        TotpCredentialScheme scheme = new TotpCredentialScheme(
                repository, new TotpCodeVerifier(), keyring, ids);

        scheme.bind(30L, new TotpCredential("JBSWY3DPEHPK3PXP", 100L));

        ArgumentCaptor<EncryptedSecret> encrypted = ArgumentCaptor.forClass(EncryptedSecret.class);
        verify(repository).save(eq(40L), eq(30L), encrypted.capture(), eq(100L));
        assertThat(keyring.decrypt("totp.credential.40.secret", encrypted.getValue()))
                .isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void shouldAcceptCurrentCodeOnlyWhenDatabaseClaimsStep() {
        TotpRepository repository = mock(TotpRepository.class);
        IdGenerator ids = mock(IdGenerator.class);
        SecretKeyring keyring = keyring();
        TotpCodeVerifier codes = new TotpCodeVerifier();
        String secret = codes.generateSecret();
        EncryptedSecret encrypted = keyring.encrypt("totp.credential.40.secret", secret);
        when(repository.find(30L)).thenReturn(new TotpRow()
                .setCredentialId(40L)
                .setAccountId(30L)
                .setSubjectId(10L)
                .setScopeType(AccountScopeType.GLOBAL)
                .setDisplayName("管理员")
                .setSecretKeyId(encrypted.getKeyId())
                .setSecretNonce(encrypted.getNonce())
                .setSecretCiphertext(encrypted.getCiphertext())
                .setLastAcceptedStep(-1L)
                .setCredentialStatus(CredentialStatus.ACTIVE)
                .setAccountStatus(AccountStatus.ACTIVE)
                .setSecondFactorRequired(true)
                .setSubjectStatus(SubjectStatus.ACTIVE)
                .setCredentialVersion(3L));
        when(repository.advanceAcceptedStep(eq(40L), eq(30L), any(Long.class)))
                .thenReturn(true, false);
        TotpCredentialScheme scheme = new TotpCredentialScheme(repository, codes, keyring, ids);
        String code = String.format("%06d", new GoogleAuthenticator().getTotpPassword(secret, Instant.now().toEpochMilli()));

        var verified = scheme.verify(30L, new TotpProof(code));

        assertThat(verified.getCredentialId()).isEqualTo(40L);
        assertThat(verified.getCredentialVersion()).isEqualTo(3L);
        assertThatThrownBy(() -> scheme.verify(30L, new TotpProof(code)))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getErrorCode())
                .isEqualTo(AuthenticationErrorCode.TOTP_CODE_INVALID);
    }

    /** @return 使用固定测试 AES-256 密钥的 keyring */
    private SecretKeyring keyring() {
        Sca4djSecretKeyringProperties properties = new Sca4djSecretKeyringProperties();
        properties.setActiveKeyId("test");
        properties.setKeys(Map.of(
                "test", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="));
        return new SecretKeyring(properties);
    }
}
