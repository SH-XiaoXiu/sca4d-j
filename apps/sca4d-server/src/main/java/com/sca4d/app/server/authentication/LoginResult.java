package com.sca4d.app.server.authentication;

import com.sca4d.core.api.authentication.AuthenticationProgress;
import com.sca4d.core.api.authentication.EstablishedSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/** 返回登录认证进度，以及因素满足后建立的可选 Session。 */
public final class LoginResult {

    private final AuthenticationProgress progress;

    private final @Nullable EstablishedSession session;

    /** 创建登录结果；认证尚未满足时 Session 为空。 */
    public LoginResult(
            @NotNull AuthenticationProgress progress,
            @Nullable EstablishedSession session) {
        this.progress = Objects.requireNonNull(progress, "登录认证进度不能为空");
        this.session = session;
    }

    /** 返回公开认证进度。 */
    public @NotNull AuthenticationProgress progress() {
        return progress;
    }

    /** 返回认证满足后建立的 Session 和 Token。 */
    public @NotNull Optional<EstablishedSession> session() {
        return Optional.ofNullable(session);
    }
}
