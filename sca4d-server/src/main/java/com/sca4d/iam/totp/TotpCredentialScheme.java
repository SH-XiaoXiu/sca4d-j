package com.sca4d.iam.totp;

import com.sca4d.authentication.error.AuthenticationErrorCode;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.CredentialType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.TotpCredential;
import com.sca4d.common.auth.TotpProof;
import com.sca4d.common.auth.UnauthenticatedException;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.common.security.EncryptedSecret;
import com.sca4d.common.security.SecretKeyring;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.credential.CredentialScheme;
import com.sca4d.iam.credential.CredentialStatus;
import com.sca4d.iam.credential.CredentialVerification;
import com.sca4d.iam.error.KnownCredentialRejectedException;
import com.sca4d.iam.subject.SubjectStatus;
import com.sca4d.iam.totp.repository.TotpRepository;
import com.sca4d.iam.totp.repository.TotpRow;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** TOTP 密钥加密、替换、验证码验证和跨节点时间步消费实现。 */
@Component
@RequiredArgsConstructor
public class TotpCredentialScheme implements CredentialScheme<TotpCredential, TotpProof> {

    private static final String UNKNOWN_SECRET = "JBSWY3DPEHPK3PXP";

    private final TotpRepository repository;
    private final TotpCodeVerifier codes;
    private final SecretKeyring keyring;
    private final IdGenerator idGenerator;

    @Override
    public @NotNull CredentialType credentialType() {
        return CredentialType.TOTP;
    }

    @Override
    public @NotNull Class<TotpCredential> credentialClass() {
        return TotpCredential.class;
    }

    @Override
    public @NotNull Class<TotpProof> proofClass() {
        return TotpProof.class;
    }

    @Override
    public @NotNull Long bind(
            @NotNull Long accountId, @NotNull TotpCredential credential) {
        Long credentialId = idGenerator.nextId();
        EncryptedSecret encrypted = keyring.encrypt(
                secretPurpose(credentialId), credential.secret());
        repository.save(credentialId, accountId, encrypted, credential.acceptedStep());
        return credentialId;
    }

    @Override
    public void replace(
            @NotNull Long accountId,
            @NotNull Long credentialId,
            @NotNull Long version,
            @NotNull TotpCredential credential) {
        EncryptedSecret encrypted = keyring.encrypt(
                secretPurpose(credentialId), credential.secret());
        repository.replace(
                credentialId, accountId, encrypted, credential.acceptedStep(), version);
    }

    @Override
    public @NotNull CredentialVerification verify(
            @NotNull Long accountId, @NotNull TotpProof proof) {
        TotpRow row = repository.find(accountId);
        if (row == null) {
            codes.matchStep(UNKNOWN_SECRET, proof.code(), Instant.now());
            throw new UnauthenticatedException(AuthenticationErrorCode.TOTP_CODE_INVALID);
        }
        String secret = keyring.decrypt(
                secretPurpose(row.getCredentialId()),
                new EncryptedSecret(
                        row.getSecretKeyId(), row.getSecretNonce(), row.getSecretCiphertext()));
        Long acceptedStep = codes.matchStep(secret, proof.code(), Instant.now());
        // 状态、验证码和原子时间步必须全部有效；同一步并发请求只有一次更新成功。
        if (row.getCredentialStatus() != CredentialStatus.ACTIVE
                || row.getAccountStatus() != AccountStatus.ACTIVE
                || row.getSubjectStatus() != SubjectStatus.ACTIVE
                || acceptedStep == null
                || !repository.advanceAcceptedStep(
                        row.getCredentialId(), row.getAccountId(), acceptedStep)) {
            throw new KnownCredentialRejectedException(
                    row.getCredentialId(), row.getSubjectId(), AuthenticationErrorCode.TOTP_CODE_INVALID);
        }
        return new CredentialVerification(
                new Subject(row.getSubjectId(), row.getDisplayName()),
                new Account(
                        row.getAccountId(),
                        row.getSubjectId(),
                        row.getScopeType(),
                        row.getScopeId(),
                        Boolean.TRUE.equals(row.getSecondFactorRequired())),
                row.getCredentialId(),
                row.getCredentialVersion());
    }

    /** @return 将 Credential 主键写入 GCM additional authenticated data 的用途编码 */
    private @NotNull String secretPurpose(@NotNull Long credentialId) {
        return "totp.credential." + credentialId + ".secret";
    }
}
