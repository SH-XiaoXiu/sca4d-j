package com.sca4d.auth.sms;

import com.sca4d.auth.sms.challenge.ConsumedSmsCodeChallenge;
import com.sca4d.auth.sms.challenge.SmsCodeChallengeService;
import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.AuthenticationMethod;
import com.sca4d.core.api.authentication.VerifiedAuthentication;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.AccountIdentifierType;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.util.Objects;
import java.util.Set;

/**
 * 单次消费 SMS Challenge，并向认证引擎返回持有因素证据。
 */
public class SmsCodeAuthenticationMethod implements AuthenticationMethod<SmsCodeProof> {
    public static final @NotNull TreeCode CODE = TreeCode.ofSlash("AUTHENTICATION/SMS_CODE");

    public static final @NotNull AccountIdentifierType PHONE_IDENTIFIER_TYPE = new AccountIdentifierType("PHONE");

    private static final Set<AuthenticationFactorKind> FACTORS = Set.of(AuthenticationFactorKind.POSSESSION);

    private final SmsCodeChallengeService challenges;

    private final Clock clock;

    /**
     * 创建 SMS Code 认证方法。
     */
    public SmsCodeAuthenticationMethod(@NotNull SmsCodeChallengeService challenges, @NotNull Clock clock) {
        this.challenges = Objects.requireNonNull(challenges, "SMS Challenge 服务不能为空");
        this.clock = Objects.requireNonNull(clock, "Clock 不能为空");
    }

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    @Override
    public @NotNull Class<SmsCodeProof> proofType() {
        return SmsCodeProof.class;
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
            @NotNull SmsCodeProof proof) {
        try {
            ConsumedSmsCodeChallenge consumed = challenges.consume(proof);
            if (expectedAccount != null && expectedAccount.accountId() != consumed.account().accountId()) {
                throw new BusinessException(SmsCodeErrorCode.CODE_REJECTED);
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
            throw new BusinessException(SmsCodeErrorCode.CODE_REJECTED, exception);
        }
    }

    @Override
    public void verifyCurrent(@NotNull Account account, @NotNull VerifiedAuthentication authentication) {
        challenges.verifyConsumed(
                new SmsCodeChallengeId(authentication.reference()),
                account.accountId(),
                authentication.version());
    }

    @Override
    public void eraseAccount(long accountId) {
        if (accountId <= 0)
            throw new IllegalArgumentException("Account 标识必须为正数");
    }
}
