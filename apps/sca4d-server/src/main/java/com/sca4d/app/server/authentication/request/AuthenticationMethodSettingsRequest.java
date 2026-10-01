package com.sca4d.app.server.authentication.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 接收一个认证方法在登录首步、后续因素和敏感动作中的开关。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean setter 反序列化请求。
public class AuthenticationMethodSettingsRequest {

    @NotBlank
    @Size(max = 64)
    private @Nullable String methodCode;

    private boolean loginStartEnabled;

    private boolean loginAdditionalEnabled;

    private boolean sensitiveActionEnabled;

    /** 创建供 JSON 反序列化使用的空请求。 */
    public AuthenticationMethodSettingsRequest() {
    }

    public @NotNull String getMethodCode() {
        return Objects.requireNonNull(methodCode, "认证方法编码不能为空");
    }

    public void setMethodCode(@Nullable String methodCode) {
        this.methodCode = methodCode;
    }

    public boolean isLoginStartEnabled() {
        return loginStartEnabled;
    }

    public void setLoginStartEnabled(boolean loginStartEnabled) {
        this.loginStartEnabled = loginStartEnabled;
    }

    public boolean isLoginAdditionalEnabled() {
        return loginAdditionalEnabled;
    }

    public void setLoginAdditionalEnabled(boolean loginAdditionalEnabled) {
        this.loginAdditionalEnabled = loginAdditionalEnabled;
    }

    public boolean isSensitiveActionEnabled() {
        return sensitiveActionEnabled;
    }

    public void setSensitiveActionEnabled(boolean sensitiveActionEnabled) {
        this.sensitiveActionEnabled = sensitiveActionEnabled;
    }
}
