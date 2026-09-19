package com.sca4d.app.server.authentication.view;

import com.sca4d.app.server.authentication.LoginResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 返回登录认证进度和满足要求后签发的 Session Token。 */
public final class LoginResultView {

    private final AuthenticationProgressView authentication;

    private final @Nullable SessionTokensView session;

    private LoginResultView(
            AuthenticationProgressView authentication,
            @Nullable SessionTokensView session) {
        this.authentication = authentication;
        this.session = session;
    }

    /** 创建示例应用登录结果的 HTTP 投影。 */
    public static @NotNull LoginResultView from(@NotNull LoginResult result) {
        LoginResult checked = Objects.requireNonNull(result, "登录结果不能为空");
        return new LoginResultView(
                AuthenticationProgressView.from(checked.progress()),
                checked.session().map(SessionTokensView::from).orElse(null));
    }

    /** 返回登录动作认证进度。 */
    public @NotNull AuthenticationProgressView getAuthentication() {
        return authentication;
    }

    /** 返回因素满足后建立的 Session Token；仍需因素时为空。 */
    public @Nullable SessionTokensView getSession() {
        return session;
    }
}
