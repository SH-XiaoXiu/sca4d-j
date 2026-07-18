package com.sca4d.authentication.passkey;

import com.sca4d.authentication.AuthMethod;
import com.sca4d.authentication.AuthMethodCapability;
import com.sca4d.authentication.AuthMethodType;
import com.sca4d.authentication.AuthMethodVerification;
import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.iam.credential.CredentialService;
import com.sca4d.iam.credential.CredentialVerification;
import com.sca4d.iam.passkey.PasskeyCredentialScheme;
import com.sca4d.iam.passkey.PasskeyProof;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Set;

/** 验证 WebAuthn Passkey assertion，并在创建 Session 或消费 STEP_UP 前复核 Credential 版本。 */
@Component
@ConditionalOnProperty(prefix = "sca4dj.authentication.passkey", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PasskeyAuthMethod implements AuthMethod<PasskeyProof> {

    private static final Set<AuthMethodCapability> CAPABILITIES = Set.of(
            AuthMethodCapability.LOGIN, AuthMethodCapability.STEP_UP);

    private final PasskeyCredentialScheme passkeys;
    private final CredentialService credentials;

    @Override
    public @NotNull AuthMethodType type() {
        return AuthMethodType.PASSKEY;
    }

    @Override
    public @NotNull Class<PasskeyProof> proofClass() {
        return PasskeyProof.class;
    }

    @Override
    public @NotNull Set<AuthMethodCapability> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public @NotNull AuthMethodVerification verify(
            @NotNull AuthenticationProcess authentication,
            @NotNull PasskeyProof proof) {
        CredentialVerification credential = passkeys.verify(
                authentication.getScopeType(), authentication.getScopeId(), proof);
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
