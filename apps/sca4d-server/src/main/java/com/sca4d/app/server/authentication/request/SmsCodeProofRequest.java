package com.sca4d.app.server.authentication.request;

import com.sca4d.auth.sms.SmsCodeChallengeId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 接收 SMS Challenge 标识和只在当前认证调用中使用的六位验证码。 */
public final class SmsCodeProofRequest {
    @NotBlank
    @Size(max = 64)
    private @Nullable String challengeId;

    @NotBlank
    @Pattern(regexp = "\\d{6}")
    private @Nullable String code;

    public SmsCodeProofRequest() {
    }

    public @NotNull SmsCodeChallengeId challengeId() {
        return new SmsCodeChallengeId(
                Objects.requireNonNull(challengeId, "SMS Challenge 标识不能为空"));
    }

    public void setChallengeId(@Nullable String challengeId) {
        this.challengeId = challengeId;
    }

    public @NotNull String getCode() {
        return Objects.requireNonNull(code, "SMS 验证码不能为空");
    }

    public void setCode(@Nullable String code) {
        this.code = code;
    }

    @Override
    public @NotNull String toString() {
        return "SmsCodeProofRequest[challengeId=" + challengeId + ", code=protected]";
    }
}
