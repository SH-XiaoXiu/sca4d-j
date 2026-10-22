package com.sca4d.app.server.authentication.view;

import com.sca4d.auth.email.EmailCodeChallenge;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 返回不包含完整邮箱和验证码的 Email Code Challenge。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean getter 序列化响应。
public class EmailCodeChallengeView {

    private final @NotNull Instant expiresAt;

    private final @NotNull Instant resendAt;

    private final @NotNull String challengeId;

    private final @NotNull String maskedRecipient;

    private EmailCodeChallengeView(@NotNull EmailCodeChallenge challenge) {
        this.challengeId = challenge.challengeId().value();
        this.maskedRecipient = challenge.maskedRecipient();
        this.expiresAt = challenge.expiresAt();
        this.resendAt = challenge.resendAt();
    }

    /** @return Email Code Challenge 的安全 HTTP 投影 */
    @CheckReturnValue
    public static @NotNull EmailCodeChallengeView from(@NotNull EmailCodeChallenge challenge) {
        return new EmailCodeChallengeView(Objects.requireNonNull(challenge, "Email Code Challenge 不能为空"));
    }

    /** @return Challenge UUID 文本 */
    public @NotNull String getChallengeId() {
        return challengeId;
    }

    /** @return 不包含完整邮箱的接收人提示 */
    public @NotNull String getMaskedRecipient() {
        return maskedRecipient;
    }

    /** @return 当前验证码到期时间 */
    public @NotNull Instant getExpiresAt() {
        return expiresAt;
    }

    /** @return 同一目标最早可以重新签发 Challenge 的时间 */
    public @NotNull Instant getResendAt() {
        return resendAt;
    }
}
