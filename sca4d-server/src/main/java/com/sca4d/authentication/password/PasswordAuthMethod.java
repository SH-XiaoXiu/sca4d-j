package com.sca4d.authentication.password;

import com.sca4d.authentication.AuthMethod;
import com.sca4d.authentication.AuthMethodCapability;
import com.sca4d.authentication.AuthMethodType;
import com.sca4d.authentication.AuthMethodVerification;
import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.entity.IamAccountIdentifierEntity;
import com.sca4d.iam.credential.CredentialService;
import com.sca4d.iam.credential.CredentialVerification;
import com.sca4d.iam.identifier.AccountIdentifierService;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Set;

/** 解析初始登录的 AccountIdentifier，或验证已固定 Account 的 PASSWORD Credential。 */
@Component
@RequiredArgsConstructor
public class PasswordAuthMethod implements AuthMethod<PasswordAuthenticationProof> {

    private static final Set<AuthMethodCapability> CAPABILITIES = Set.of(AuthMethodCapability.LOGIN, AuthMethodCapability.STEP_UP);

    private final AccountIdentifierService accountIdentifierService;
    private final CredentialService credentialService;

    @Override
    public @NotNull AuthMethodType type() {
        return AuthMethodType.PASSWORD;
    }

    @Override
    public @NotNull Class<PasswordAuthenticationProof> proofClass() {
        return PasswordAuthenticationProof.class;
    }

    @Override
    public @NotNull Set<AuthMethodCapability> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public @NotNull AuthMethodVerification verify(
            @NotNull AuthenticationProcess authentication,
            @NotNull PasswordAuthenticationProof proof) {
        Long accountId = authentication.getAccountId();
        if (accountId == null) {
            // Identifier 只负责在请求作用域定位 Account；缺失或未知标识仍进入固定不存在账号的等价密码验证路径。
            IamAccountIdentifierEntity identifier = proof.identifierType() == null || proof.identifier() == null
                    ? null
                    : accountIdentifierService.findActive(
                            authentication.getScopeType(),
                            authentication.getScopeId(),
                            proof.identifierType(),
                            proof.identifier());
            accountId = identifier == null ? -1L : identifier.getAccountId();
        }
        CredentialVerification verification = credentialService.verify(accountId, proof.password());
        return new AuthMethodVerification(
                type(),
                verification.getSubject(),
                verification.getAccount(),
                verification.getCredentialId().toString(),
                verification.getCredentialVersion());
    }

    @Override
    public @NotNull AuthMethodVerification requireCurrent(
            @NotNull AuthMethodVerification verification) {
        // CredentialService 复核 Credential、Account 和 Subject 的状态及版本，本方式只转换 Credential ID。
        CredentialVerification current = credentialService.requireCurrent(new CredentialVerification(
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
