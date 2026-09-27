package com.sca4d.app.server.authentication.view;

import static com.sca4d.core.api.name.NameFormat.SLASH;

import com.sca4d.app.server.authentication.AccountAuthenticationSettings;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.setting.SettingValue;
import com.sca4d.core.spring.web.annotation.ETag;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 返回当前账号的动作认证因素数量和认证方法开关。
 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean getter 序列化响应。
public final class AuthenticationSettingsView {

    private final int loginFactorCount;

    private final int sensitiveActionFactorCount;

    private final long reusableAuthenticationMaximumAgeSeconds;

    private final List<AuthenticationMethodSettingsView> methods;

    private final @NotNull Version revision;

    private AuthenticationSettingsView(SettingValue<AccountAuthenticationSettings> setting) {
        AccountAuthenticationSettings settings = setting.value();
        this.loginFactorCount = settings.loginFactorCount();
        this.sensitiveActionFactorCount = settings.sensitiveActionFactorCount();
        this.reusableAuthenticationMaximumAgeSeconds = settings.reusableAuthenticationMaximumAge().toSeconds();
        this.methods = settings.methods()
                .values()
                .stream()
                .sorted(Comparator.comparing(item -> item.methodCode().format(SLASH)))
                .map(AuthenticationMethodSettingsView::from)
                .toList();
        this.revision = setting.version();
    }

    /**
     * 创建账号认证设置的 HTTP 投影。
     */
    public static @NotNull AuthenticationSettingsView from(
            @NotNull SettingValue<AccountAuthenticationSettings> setting) {
        return new AuthenticationSettingsView(Objects.requireNonNull(setting, "账号认证设置不能为空"));
    }

    public int getLoginFactorCount() {
        return loginFactorCount;
    }

    public int getSensitiveActionFactorCount() {
        return sensitiveActionFactorCount;
    }

    public long getReusableAuthenticationMaximumAgeSeconds() {
        return reusableAuthenticationMaximumAgeSeconds;
    }

    public @NotNull List<AuthenticationMethodSettingsView> getMethods() {
        return List.copyOf(methods);
    }

    /** @return 生成当前完整认证设置的版本 */
    @ETag
    public @NotNull Version revision() {
        return revision;
    }
}
