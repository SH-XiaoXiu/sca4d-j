package com.sca4d.app.server.authentication.request;

import com.sca4d.auth.email.EmailCodeChallengeId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 接收 Email Code Challenge 标识和只在当前认证调用中使用的六位验证码。 */
public final class EmailCodeProofRequest {

    @NotBlank
    @Size(max = 64)
    private @Nullable String challengeId;

    @NotBlank
    @Pattern(regexp = "\\d{6}")
    private @Nullable String code;

    /** 创建供 JSON 反序列化使用的空请求。 */
    public EmailCodeProofRequest() {
    }

    /** @return 已签发 Challenge 的强类型标识 */
    public @NotNull EmailCodeChallengeId challengeId() {
        return new EmailCodeChallengeId(
                Objects.requireNonNull(challengeId, "Email Code Challenge 标识不能为空"));
    }

    /** @param challengeId 已签发 Challenge 的 UUID 文本 */
    public void setChallengeId(@Nullable String challengeId) {
        this.challengeId = challengeId;
    }

    /** @return 调用方收到的六位验证码 */
    public @NotNull String getCode() {
        return Objects.requireNonNull(code, "Email 验证码不能为空");
    }

    /** @param code 调用方收到的六位验证码 */
    public void setCode(@Nullable String code) {
        this.code = code;
    }

    @Override
    public @NotNull String toString() {
        return "EmailCodeProofRequest[challengeId=" + challengeId + ", code=protected]";
    }
}
