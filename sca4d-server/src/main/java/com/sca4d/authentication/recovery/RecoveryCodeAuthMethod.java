package com.sca4d.authentication.recovery;

import com.sca4d.authentication.AuthMethod;
import com.sca4d.authentication.AuthMethodCapability;
import com.sca4d.authentication.AuthMethodType;
import com.sca4d.authentication.AuthMethodVerification;
import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.entity.IamAccountIdentifierEntity;
import com.sca4d.iam.identifier.AccountIdentifierService;
import com.sca4d.iam.recovery.RecoveryCodeService;
import com.sca4d.iam.recovery.RecoveryCodeVerification;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Set;

/** 解析账号范围内的 AccountIdentifier，并校验一次性恢复码。 */
@Component
@RequiredArgsConstructor
public class RecoveryCodeAuthMethod implements AuthMethod<RecoveryCodeAuthenticationProof> {

    private static final Set<AuthMethodCapability> CAPABILITIES = Set.of(AuthMethodCapability.RECOVERY);

    private final AccountIdentifierService accountIdentifierService;
    private final RecoveryCodeService recoveryCodeService;

    @Override
    public @NotNull AuthMethodType type() {
        return AuthMethodType.RECOVERY_CODE;
    }

    @Override
    public @NotNull Class<RecoveryCodeAuthenticationProof> proofClass() {
        return RecoveryCodeAuthenticationProof.class;
    }

    @Override
    public @NotNull Set<AuthMethodCapability> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public @NotNull AuthMethodVerification verify(
            @NotNull AuthenticationProcess authentication,
            @NotNull RecoveryCodeAuthenticationProof proof) {
        // 未知 Identifier 使用固定不存在 Account 继续执行相同数量的 Argon2id 校验，避免暴露账号是否存在。
        IamAccountIdentifierEntity identifier = accountIdentifierService.findActive(
                authentication.getScopeType(),
                authentication.getScopeId(),
                proof.identifierType(),
                proof.identifier());
        Long accountId = identifier == null ? -1L : identifier.getAccountId();
        RecoveryCodeVerification verification = recoveryCodeService.consume(accountId, proof.recoveryCode());
        return new AuthMethodVerification(
                type(),
                verification.getSubject(),
                verification.getAccount(),
                verification.getCodeId().toString(),
                verification.getSetVersion());
    }

    @Override
    public @NotNull AuthMethodVerification requireCurrent(
            @NotNull AuthMethodVerification verification) {
        // 恢复码明文已经销毁；复核只使用已消费记录主键和当前集合版本，不允许再次校验或再次消费恢复码。
        RecoveryCodeVerification current = recoveryCodeService.requireCurrent(new RecoveryCodeVerification(
                verification.getSubject(),
                verification.getAccount(),
                Long.valueOf(verification.getProofId()),
                verification.getProofVersion()));
        return new AuthMethodVerification(
                type(),
                current.getSubject(),
                current.getAccount(),
                current.getCodeId().toString(),
                current.getSetVersion());
    }
}
