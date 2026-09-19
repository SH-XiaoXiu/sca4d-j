package com.sca4d.app.server.authentication.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 接收匿名登录调用方已经知道的邮箱，不在字符串表示中暴露地址。 */
public final class EmailCodeChallengeRequest {

    @NotBlank
    @Email
    @Size(max = 320)
    private @Nullable String email;

    /** 创建供 JSON 反序列化使用的空请求。 */
    public EmailCodeChallengeRequest() {
    }

    /** @return 用于签发匿名一致 Challenge 的邮箱登录标识 */
    public @NotNull String getEmail() {
        return Objects.requireNonNull(email, "邮箱不能为空");
    }

    /** @param email 调用方输入的邮箱登录标识 */
    public void setEmail(@Nullable String email) {
        this.email = email;
    }

    @Override
    public @NotNull String toString() {
        return "EmailCodeChallengeRequest[email=protected]";
    }
}
