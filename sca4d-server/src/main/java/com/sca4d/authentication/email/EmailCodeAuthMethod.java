package com.sca4d.authentication.email;

import com.sca4d.authentication.AuthMethod;
import com.sca4d.authentication.AuthMethodCapability;
import com.sca4d.authentication.AuthMethodType;
import com.sca4d.authentication.AuthMethodVerification;
import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.authentication.email.codec.EmailCodeChallengeReference;
import com.sca4d.authentication.error.AuthenticationErrorCode;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

/** 消费并复核通过邮件投递的一次性验证码 Challenge。 */
@Component
@ConditionalOnProperty(prefix = "sca4dj.authentication.email-code", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class EmailCodeAuthMethod implements AuthMethod<EmailCodeAuthenticationProof> {

    private static final Set<AuthMethodCapability> CAPABILITIES = Set.of(AuthMethodCapability.STEP_UP);

    private final EmailCodeChallengeService challenges;

    @Override
    public @NotNull AuthMethodType type() {
        return AuthMethodType.EMAIL_CODE;
    }

    @Override
    public @NotNull Class<EmailCodeAuthenticationProof> proofClass() {
        return EmailCodeAuthenticationProof.class;
    }

    @Override
    public @NotNull Set<AuthMethodCapability> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public @NotNull AuthMethodVerification verify(
            @NotNull AuthenticationProcess authentication,
            @NotNull EmailCodeAuthenticationProof proof) {
        // Challenge 在 Redis 中原子消费；Challenge Service 检查错误次数、过期时间和并发重放。
        EmailCodeChallenge challenge = challenges.consume(
                proof.getAuthenticationId(), proof.getChallengeId(), proof.getCode());
        // 已消费 Challenge 必须与当前认证的 Subject、Account 和账号范围一致，禁止跨认证复用验证码。
        if (!authentication.getSubjectId().equals(challenge.getSubjectId())
                || !authentication.getAccountId().equals(challenge.getAccountId())
                || authentication.getScopeType() != challenge.getScopeType()
                || !Objects.equals(authentication.getScopeId(), challenge.getScopeId())) {
            throw new BizException(AuthenticationErrorCode.AUTHENTICATION_CHANGED);
        }
        // Flowable 只保存认证 UUID、Challenge UUID 和版本号，验证码与完整邮箱不会进入 Flowable 或 Session。
        return new AuthMethodVerification(
                type(),
                new Subject(authentication.getSubjectId(), null),
                new Account(
                        authentication.getAccountId(),
                        authentication.getSubjectId(),
                        authentication.getScopeType(),
                        authentication.getScopeId(),
                        authentication.isSecondFactorRequired()),
                new EmailCodeChallengeReference(
                        proof.getAuthenticationId(), proof.getChallengeId())
                        .value(),
                challenge.getVersion());
    }

    @Override
    public @NotNull AuthMethodVerification requireCurrent(
            @NotNull AuthMethodVerification verification) {
        // 创建 Session 前重新读取已消费 Challenge，确认版本和消费状态没有变化。
        EmailCodeChallengeReference reference = EmailCodeChallengeReference.parse(verification.getProofId());
        challenges.requireConsumed(
                reference.getAuthenticationId(), reference.getChallengeId(), verification.getProofVersion());
        return verification;
    }
}
