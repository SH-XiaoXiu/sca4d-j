package com.sca4d.app.server.authentication.view;

import com.sca4d.core.api.authentication.EstablishedSession;
import com.sca4d.core.api.authentication.SessionTokens;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/** 返回登录或刷新后交付的 access Token、可选 Session Token 和各自有效期。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean getter 序列化响应。
public class SessionTokensView {

    private final @NotNull String tokenMode;

    private final @NotNull String tokenType;

    private final @NotNull String accessToken;

    private final @NotNull Instant accessTokenExpiresAt;

    private final @Nullable String sessionToken;

    private final @NotNull String sessionId;

    private final @NotNull Instant sessionExpiresAt;

    private SessionTokensView(@NotNull SessionTokens tokens) {
        this.tokenMode = tokens.tokenMode().name();
        this.tokenType = tokens.tokenType();
        this.accessToken = tokens.accessToken();
        this.accessTokenExpiresAt = tokens.accessTokenExpiresAt();
        this.sessionToken = tokens.sessionToken();
        this.sessionId = tokens.sessionId();
        this.sessionExpiresAt = tokens.sessionExpiresAt();
    }

    /**
     * 从已建立的 Session 创建 HTTP Token 响应。
     *
     * @param establishment 已建立的 Session 及其 Token
     * @return HTTP Token 响应
     */
    @CheckReturnValue
    public static @NotNull SessionTokensView from(@NotNull EstablishedSession establishment) {
        return from(Objects.requireNonNull(establishment, "Session 建立结果不能为空").tokens());
    }

    /**
     * 从登录或刷新签发的 Token 创建 HTTP 响应。
     *
     * @param tokens 登录或刷新签发的 Token
     * @return HTTP Token 响应
     */
    @CheckReturnValue
    public static @NotNull SessionTokensView from(@NotNull SessionTokens tokens) {
        return new SessionTokensView(Objects.requireNonNull(tokens, "Session Token 不能为空"));
    }

    /**
     * 返回普通请求默认使用的 Token 模式。
     *
     * @return 普通请求默认使用的 Token 模式
     */
    public @NotNull String getTokenMode() {
        return tokenMode;
    }

    /**
     * 返回 HTTP Authorization 认证方案。
     *
     * @return HTTP Authorization 认证方案
     */
    public @NotNull String getTokenType() {
        return tokenType;
    }

    /**
     * 返回普通请求使用的 Token。
     *
     * @return 普通请求使用的 Token
     */
    public @NotNull String getAccessToken() {
        return accessToken;
    }

    /**
     * 返回 access Token 过期时间。
     *
     * @return access Token 过期时间
     */
    public @NotNull Instant getAccessTokenExpiresAt() {
        return accessTokenExpiresAt;
    }

    /**
     * 返回 JWT 模式下的在线 Session Token。
     *
     * @return JWT 模式下的在线 Session Token；SESSION 模式为空
     */
    public @Nullable String getSessionToken() {
        return sessionToken;
    }

    /**
     * 返回 backing Session 标识。
     *
     * @return backing Session 标识
     */
    public @NotNull String getSessionId() {
        return sessionId;
    }

    /**
     * 返回 backing Session 过期时间。
     *
     * @return backing Session 过期时间
     */
    public @NotNull Instant getSessionExpiresAt() {
        return sessionExpiresAt;
    }
}
