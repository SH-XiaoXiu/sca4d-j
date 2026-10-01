package com.sca4d.app.server.authentication.view;

import com.sca4d.auth.sms.SmsCodeChallenge;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 返回不包含完整手机号和验证码的 SMS Challenge。 */
@SuppressWarnings("unused")
public class SmsCodeChallengeView {
    private final String challengeId;

    private final String maskedRecipient;

    private final Instant expiresAt;

    private final Instant resendAt;

    /** 创建 SMS Challenge 响应视图。 */
    private SmsCodeChallengeView(SmsCodeChallenge challenge) {
        this.challengeId = challenge.challengeId().value();
        this.maskedRecipient = challenge.maskedRecipient();
        this.expiresAt = challenge.expiresAt();
        this.resendAt = challenge.resendAt();
    }

    /** @param challenge SMS Challenge @return 脱敏后的响应视图 */
    public static @NotNull SmsCodeChallengeView from(@NotNull SmsCodeChallenge challenge) {
        return new SmsCodeChallengeView(Objects.requireNonNull(challenge, "SMS Challenge 不能为空"));
    }

    /** @return Challenge 标识 */
    public String getChallengeId() {
        return challengeId;
    }

    /** @return 脱敏后的接收方 */
    public String getMaskedRecipient() {
        return maskedRecipient;
    }

    /** @return 到期时间 */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /** @return 允许重新发送的时间 */
    public Instant getResendAt() {
        return resendAt;
    }
}
