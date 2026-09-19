package com.sca4d.app.server.authentication.view;

import com.sca4d.auth.sms.SmsCodeChallenge;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 返回不包含完整手机号和验证码的 SMS Challenge。 */
@SuppressWarnings("unused")
public final class SmsCodeChallengeView {
    private final String challengeId;

    private final String maskedRecipient;

    private final Instant expiresAt;

    private final Instant resendAt;

    private SmsCodeChallengeView(SmsCodeChallenge challenge) {
        this.challengeId = challenge.challengeId().value();
        this.maskedRecipient = challenge.maskedRecipient();
        this.expiresAt = challenge.expiresAt();
        this.resendAt = challenge.resendAt();
    }

    public static @NotNull SmsCodeChallengeView from(@NotNull SmsCodeChallenge challenge) {
        return new SmsCodeChallengeView(Objects.requireNonNull(challenge, "SMS Challenge 不能为空"));
    }

    public String getChallengeId() {
        return challengeId;
    }

    public String getMaskedRecipient() {
        return maskedRecipient;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getResendAt() {
        return resendAt;
    }
}
