package com.sca4d.app.server.authentication.request;

import com.sca4d.core.spring.validation.PhoneNumber;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 接收匿名登录调用方已经知道的规范 E.164 手机号。 */
public final class SmsCodeChallengeRequest {
    @NotBlank
    @Size(max = 32)
    @PhoneNumber
    private @Nullable String phoneNumber;

    public SmsCodeChallengeRequest() {
    }

    public @NotNull String getPhoneNumber() {
        return Objects.requireNonNull(phoneNumber, "手机号不能为空");
    }

    public void setPhoneNumber(@Nullable String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    @Override
    public @NotNull String toString() {
        return "SmsCodeChallengeRequest[phoneNumber=protected]";
    }
}
