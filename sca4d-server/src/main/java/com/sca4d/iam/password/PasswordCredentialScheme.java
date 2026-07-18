package com.sca4d.iam.password;

import com.sca4d.authentication.error.AuthenticationErrorCode;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.CredentialType;
import com.sca4d.common.auth.PasswordCredential;
import com.sca4d.common.auth.PasswordProof;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.UnauthenticatedException;
import com.sca4d.common.exception.BizException;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.credential.CredentialScheme;
import com.sca4d.iam.credential.CredentialStatus;
import com.sca4d.iam.credential.CredentialVerification;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.error.KnownCredentialRejectedException;
import com.sca4d.iam.password.repository.PasswordRepository;
import com.sca4d.iam.password.repository.PasswordRow;
import com.sca4d.iam.subject.SubjectStatus;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** PASSWORD Credential 的 Argon2id 绑定、替换和指定 Account 验证实现。 */
@Component
@RequiredArgsConstructor
public class PasswordCredentialScheme implements CredentialScheme<PasswordCredential, PasswordProof> {

    private static final PasswordEncoder PASSWORDS = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    private static final String UNKNOWN_PASSWORD_HASH = PASSWORDS.encode("unknown-account-password");

    private final PasswordRepository repository;

    @Override
    public @NotNull CredentialType credentialType() {
        return CredentialType.PASSWORD;
    }

    @Override
    public @NotNull Class<PasswordCredential> credentialClass() {
        return PasswordCredential.class;
    }

    @Override
    public @NotNull Class<PasswordProof> proofClass() {
        return PasswordProof.class;
    }

    @Override
    public @NotNull Long bind(
            @NotNull Long accountId, @NotNull PasswordCredential credential) {
        if (!StringUtils.hasText(credential.password())) {
            throw new BizException(IamErrorCode.CREDENTIAL_PASSWORD_REQUIRED);
        }
        return repository.save(accountId, PASSWORDS.encode(credential.password()));
    }

    @Override
    public void replace(
            @NotNull Long accountId,
            @NotNull Long credentialId,
            @NotNull Long version,
            @NotNull PasswordCredential credential) {
        if (!StringUtils.hasText(credential.password())) {
            throw new BizException(IamErrorCode.CREDENTIAL_PASSWORD_REQUIRED);
        }
        repository.replace(credentialId, accountId, PASSWORDS.encode(credential.password()), version);
    }

    @Override
    public @NotNull CredentialVerification verify(
            @NotNull Long accountId, @NotNull PasswordProof proof) {
        if (!StringUtils.hasText(proof.password())) {
            throw new BizException(IamErrorCode.CREDENTIAL_PASSWORD_REQUIRED);
        }
        String password = proof.password();
        PasswordRow row = repository.find(accountId);
        if (row == null) {
            PASSWORDS.matches(password, UNKNOWN_PASSWORD_HASH);
            throw new UnauthenticatedException(AuthenticationErrorCode.BAD_CREDENTIAL);
        }
        if (row.getCredentialStatus() != CredentialStatus.ACTIVE
                || row.getAccountStatus() != AccountStatus.ACTIVE
                || row.getSubjectStatus() != SubjectStatus.ACTIVE
                || !PASSWORDS.matches(password, row.getPasswordHash())) {
            throw new KnownCredentialRejectedException(row.getCredentialId(), row.getSubjectId());
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
}
