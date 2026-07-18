package com.sca4d.authentication.email;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/** 已投递邮件验证码 Challenge 的安全展示结果。 */
public final class EmailCodeChallengeDelivery {

    private final String challengeId;
    private final String maskedRecipient;
    private final Instant expiresAt;
    private final Instant resendAt;

    public EmailCodeChallengeDelivery(
            @NotNull String challengeId,
            @NotNull String maskedRecipient,
            @NotNull Instant expiresAt,
            @NotNull Instant resendAt) {
        this.challengeId = challengeId;
        this.maskedRecipient = maskedRecipient;
        this.expiresAt = expiresAt;
        this.resendAt = resendAt;
    }

    public @NotNull String getChallengeId() {
        return challengeId;
    }

    public @NotNull String getMaskedRecipient() {
        return maskedRecipient;
    }

    public @NotNull Instant getExpiresAt() {
        return expiresAt;
    }

    public @NotNull Instant getResendAt() {
        return resendAt;
    }
}
