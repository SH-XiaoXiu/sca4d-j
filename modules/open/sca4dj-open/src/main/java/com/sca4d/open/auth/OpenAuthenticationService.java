package com.sca4d.open.auth;

import com.sca4d.auth.session.SessionService;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.spring.web.BearerToken;
import com.sca4d.open.oauth.OpenJwtTokenService;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/** 恢复平台开放调用使用的 Bearer Token，不决定具体协议的路由或字段。 */
public class OpenAuthenticationService {

    private final @NotNull OpenJwtTokenService jwtTokens;

    private final @NotNull SessionService sessions;

    /** 创建平台开放认证服务。 */
    public OpenAuthenticationService(
            @NotNull OpenJwtTokenService jwtTokens,
            @NotNull SessionService sessions) {
        this.jwtTokens = Objects.requireNonNull(jwtTokens, "Open JWT 服务不能为空");
        this.sessions = Objects.requireNonNull(sessions, "Session 服务不能为空");
    }

    /**
     * 从 Authorization 请求头恢复平台开放 Session。
     *
     * @param authorization Authorization 请求头；格式不正确或 Token 无效时返回空结果
     * @return 已恢复的 Session
     */
    @CheckReturnValue
    public @NotNull Optional<Session> restoreAuthorization(@Nullable String authorization) {
        if (authorization == null) {
            return Optional.empty();
        }
        Optional<String> token = BearerToken.parse(authorization);
        if (token.isEmpty()) {
            return Optional.empty();
        }
        return restore(token.get());
    }

    /**
     * 恢复平台开放 Token。
     *
     * @param token 去除 Bearer 前缀的 Token
     * @return 已恢复的 Session
     */
    @CheckReturnValue
    public @NotNull Optional<Session> restore(@NotNull String token) {
        String checked = Objects.requireNonNull(token, "Token 不能为空");
        Optional<Session> jwt = jwtTokens.restore(checked);
        return jwt.isPresent() ? jwt : sessions.currentSession(checked, false);
    }
}
