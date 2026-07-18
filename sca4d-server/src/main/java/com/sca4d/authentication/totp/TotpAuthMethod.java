package com.sca4d.authentication.totp;

import com.sca4d.authentication.AuthMethod;
import com.sca4d.authentication.AuthMethodCapability;
import com.sca4d.authentication.AuthMethodType;
import com.sca4d.authentication.AuthMethodVerification;
import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.authentication.error.AuthenticationErrorCode;
import com.sca4d.common.auth.TotpProof;
import com.sca4d.common.exception.BizException;
import com.sca4d.iam.credential.CredentialService;
import com.sca4d.iam.credential.CredentialVerification;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

/** 验证当前认证已经确定账号的 TOTP 验证码，并在创建 Session 前复核 Credential 版本。 */
@Component
@RequiredArgsConstructor
public class TotpAuthMethod implements AuthMethod<TotpAuthenticationProof> {

    private static final Set<AuthMethodCapability> CAPABILITIES = Set.of(AuthMethodCapability.STEP_UP);

    private final CredentialService credentials;

    @Override
    public @NotNull AuthMethodType type() {
        return AuthMethodType.TOTP;
    }

    @Override
    public @NotNull Class<TotpAuthenticationProof> proofClass() {
        return TotpAuthenticationProof.class;
    }

    @Override
    public @NotNull Set<AuthMethodCapability> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public @NotNull AuthMethodVerification verify(
            @NotNull AuthenticationProcess authentication,
            @NotNull TotpAuthenticationProof proof) {
        if (authentication.getAccountId() == null) {
            throw new BizException(AuthenticationErrorCode.AUTHENTICATION_CHANGED);
        }
        CredentialVerification credential = credentials.verify(
                authentication.getAccountId(), new TotpProof(proof.code()));
        if (credential.getAccount().getScopeType() != authentication.getScopeType()
                || !Objects.equals(
                        credential.getAccount().getScopeId(), authentication.getScopeId())) {
            throw new BizException(AuthenticationErrorCode.AUTHENTICATION_CHANGED);
        }
        return new AuthMethodVerification(
                type(),
                credential.getSubject(),
                credential.getAccount(),
                credential.getCredentialId().toString(),
                credential.getCredentialVersion());
    }

    @Override
    public @NotNull AuthMethodVerification requireCurrent(
            @NotNull AuthMethodVerification verification) {
        CredentialVerification current = credentials.requireCurrent(new CredentialVerification(
                verification.getSubject(),
                verification.getAccount(),
                Long.valueOf(verification.getProofId()),
                verification.getProofVersion()));
        return new AuthMethodVerification(
                type(),
                current.getSubject(),
                current.getAccount(),
                current.getCredentialId().toString(),
                current.getCredentialVersion());
    }
}
