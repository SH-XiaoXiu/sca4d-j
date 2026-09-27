package com.sca4d.app.server.authentication.view;

import static com.sca4d.core.api.name.NameFormat.SLASH;

import com.sca4d.app.server.authentication.AuthenticationMethodSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 返回一个认证方法在登录和敏感动作中的开关。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean getter 序列化响应。
public class AuthenticationMethodSettingsView {

    private final String methodCode;

    private final boolean loginStartEnabled;

    private final boolean loginAdditionalEnabled;

    private final boolean sensitiveActionEnabled;

    private AuthenticationMethodSettingsView(AuthenticationMethodSettings settings) {
        this.methodCode = settings.methodCode().format(SLASH);
        this.loginStartEnabled = settings.loginStartEnabled();
        this.loginAdditionalEnabled = settings.loginAdditionalEnabled();
        this.sensitiveActionEnabled = settings.sensitiveActionEnabled();
    }

    /** 创建认证方法设置的 HTTP 投影。 */
    public static @NotNull AuthenticationMethodSettingsView from(
            @NotNull AuthenticationMethodSettings settings) {
        return new AuthenticationMethodSettingsView(
                Objects.requireNonNull(settings, "认证方法设置不能为空"));
    }

    public String getMethodCode() {
        return methodCode;
    }

    public boolean isLoginStartEnabled() {
        return loginStartEnabled;
    }

    public boolean isLoginAdditionalEnabled() {
        return loginAdditionalEnabled;
    }

    public boolean isSensitiveActionEnabled() {
        return sensitiveActionEnabled;
    }
}
