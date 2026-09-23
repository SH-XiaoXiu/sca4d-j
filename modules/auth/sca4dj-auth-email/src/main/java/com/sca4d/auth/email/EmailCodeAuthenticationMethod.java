package com.sca4d.auth.email;

import com.sca4d.auth.email.challenge.ConsumedEmailCodeChallenge;
import com.sca4d.auth.email.challenge.EmailCodeChallengeService;
import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.AuthenticationMethod;
import com.sca4d.core.api.authentication.VerifiedAuthentication;
import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.AccountIdentifierType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.util.Objects;
import java.util.Set;

/**
 * 单次消费 Email Code Challenge，并向认证引擎返回持有因素证据。
 */
public final class EmailCodeAuthenticationMethod implements AuthenticationMethod<EmailCodeProof> {

    /**
     * Email Code 方法稳定编码。
     */
    public static final @NotNull TreeCode CODE = TreeCode.ofSlash("AUTHENTICATION/EMAIL_CODE");

    /**
     * Email 登录标识类型由 auth-email 自己定义，Core 和 IAM 保持开放类型。
     */
    public static final @NotNull AccountIdentifierType EMAIL_IDENTIFIER_TYPE = new AccountIdentifierType("EMAIL");

    private static final Set<AuthenticationFactorKind> FACTORS = Set.of(AuthenticationFactorKind.POSSESSION);

    private final @NotNull EmailCodeChallengeService challenges;

    private final @NotNull Clock clock;

    /**
     * @param challenges Challenge 签发、消费和复核服务 @param clock 认证时间来源
     */
    public EmailCodeAuthenticationMethod(
            @NotNull EmailCodeChallengeService challenges,
            @NotNull Clock clock) {
        this.challenges = Objects.requireNonNull(challenges, "EmailCodeChallengeService 不能为空");
        this.clock = Objects.requireNonNull(clock, "Clock 不能为空");
    }

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    @Override
    public @NotNull Class<EmailCodeProof> proofType() {
        return EmailCodeProof.class;
    }

    @Override
    public @NotNull Set<AuthenticationFactorKind> factorKinds() {
        return FACTORS;
    }

    @Override
    public boolean canIdentifyAccount() {
        return true;
    }

    @Override
    public boolean available(@NotNull Account account) {
        return challenges.available(account);
    }

    @Override
    public @NotNull VerifiedAuthentication authenticate(
            @Nullable Account expectedAccount,
            @NotNull EmailCodeProof proof) {
        try {
            ConsumedEmailCodeChallenge consumed = challenges.consume(proof);
            if (expectedAccount != null && expectedAccount.accountId() != consumed.account().accountId()) {
                throw new BusinessException(EmailCodeErrorCode.CODE_REJECTED);
            }
            Authentication authentication = new Authentication(
                    consumed.account().accountId(),
                    CODE,
                    FACTORS,
                    clock.instant());
            return new VerifiedAuthentication(
                    consumed.account(),
                    authentication,
                    consumed.challengeId().value(),
                    consumed.version(),
                    null);
        } catch (BusinessException exception) {
            throw new BusinessException(EmailCodeErrorCode.CODE_REJECTED, exception);
        }
    }

    @Override
    public void verifyCurrent(@NotNull Account account, @NotNull VerifiedAuthentication authentication) {
        challenges.verifyConsumedChallenge(
                new EmailCodeChallengeId(authentication.reference()),
                account.accountId(),
                authentication.version());
    }

    /**
     * Email Code 不保存 Account Credential；Challenge 由 Redis TTL 自动过期，因此终态删除无需额外持久化操作。
     *
     * @param accountId 终态删除的 Account 标识
     */
    @Override
    public void eraseAccount(long accountId) {
        if (accountId <= 0) {
            throw new IllegalArgumentException("Account 标识必须为正数");
        }
    }
}
