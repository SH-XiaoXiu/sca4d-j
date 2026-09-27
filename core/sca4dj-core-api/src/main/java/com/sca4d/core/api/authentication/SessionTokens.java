package com.sca4d.core.api.authentication;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/** 保存客户端访问当前 Session 所需的 access Token 和可选在线 Session Token。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class SessionTokens {

    private final AuthTokenMode tokenMode;

    private final String tokenType;

    private final String accessToken;

    private final Instant accessTokenExpiresAt;

    private final @Nullable String sessionToken;

    private final String sessionId;

    private final Instant sessionExpiresAt;

    /**
     * 创建一次 Token 交付结果。
     *
     * @param tokenMode 普通请求默认使用的 Token 模式
     * @param tokenType HTTP Authorization 认证方案，当前固定为 Bearer
     * @param accessToken 普通请求使用的 Token
     * @param accessTokenExpiresAt access Token 过期时间
     * @param sessionToken JWT 模式下用于在线操作的不透明 Token；SESSION 模式为空
     * @param sessionId backing Session 标识
     * @param sessionExpiresAt backing Session 过期时间
     */
    public SessionTokens(
            @NotNull AuthTokenMode tokenMode,
            @NotNull String tokenType,
            @NotNull String accessToken,
            @NotNull Instant accessTokenExpiresAt,
            @Nullable String sessionToken,
            @NotNull String sessionId,
            @NotNull Instant sessionExpiresAt) {
        this.tokenMode = Objects.requireNonNull(tokenMode, "Token 模式不能为空");
        this.tokenType = requireText(tokenType, "Token 类型");
        this.accessToken = requireText(accessToken, "access Token");
        this.accessTokenExpiresAt = Objects.requireNonNull(accessTokenExpiresAt, "access Token 过期时间不能为空");
        this.sessionToken = sessionToken == null ? null : requireText(sessionToken, "Session Token");
        this.sessionId = requireText(sessionId, "Session 标识");
        this.sessionExpiresAt = Objects.requireNonNull(sessionExpiresAt, "Session 过期时间不能为空");
        if (this.accessTokenExpiresAt.isAfter(this.sessionExpiresAt)) {
            throw new IllegalArgumentException("access Token 不能晚于 backing Session 过期");
        }
        if (this.tokenMode == AuthTokenMode.JWT && this.sessionToken == null) {
            throw new IllegalArgumentException("JWT 模式必须交付 Session Token");
        }
        if (this.tokenMode == AuthTokenMode.SESSION && this.sessionToken != null) {
            throw new IllegalArgumentException("SESSION 模式不能重复交付 Session Token");
        }
    }

    /** @return 普通请求默认使用的 Token 模式 */
    public @NotNull AuthTokenMode tokenMode() {
        return tokenMode;
    }

    /** @return HTTP Authorization 认证方案 */
    public @NotNull String tokenType() {
        return tokenType;
    }

    /** @return 普通请求使用且不能写入日志的 Token */
    public @NotNull String accessToken() {
        return accessToken;
    }

    /** @return access Token 过期时间 */
    public @NotNull Instant accessTokenExpiresAt() {
        return accessTokenExpiresAt;
    }

    /** @return JWT 模式下的在线 Session Token；SESSION 模式为空 */
    public @Nullable String sessionToken() {
        return sessionToken;
    }

    /** @return backing Session 标识 */
    public @NotNull String sessionId() {
        return sessionId;
    }

    /** @return backing Session 过期时间 */
    public @NotNull Instant sessionExpiresAt() {
        return sessionExpiresAt;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return value;
    }
}
