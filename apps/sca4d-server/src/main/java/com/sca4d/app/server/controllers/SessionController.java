package com.sca4d.app.server.controllers;

import com.sca4d.app.server.authentication.view.CurrentSessionView;
import com.sca4d.app.server.authentication.view.SessionTokensView;
import com.sca4d.auth.session.SessionService;
import com.sca4d.core.api.authentication.AuthErrorCode;
import com.sca4d.core.api.authentication.RequireSessionToken;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionTerminationReason;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.spring.validation.NoDefaultValidation;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.core.spring.web.BearerToken;
import com.sca4d.core.spring.web.annotation.CurrentSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/** 读取和注销完整示例 App 的当前登录 Session。 */
@RestController
@RequestMapping("/sessions/current")
public class SessionController {

    private final SessionService sessions;

    /**
     * 创建当前 Session Controller。
     *
     * @param sessions Session 服务
     */
    public SessionController(@NotNull SessionService sessions) {
        this.sessions = Objects.requireNonNull(sessions, "Session 服务不能为空");
    }

    /**
     * 读取当前已认证 Session。
     *
     * @param session 当前已认证 Session
     * @return 当前 Session 的公开身份投影
     */
    @GetMapping
    public @NotNull ApiResponse<CurrentSessionView> current(@CurrentSession @NotNull Session session) {
        return ApiResponse.ok(CurrentSessionView.from(session));
    }

    /**
     * 注销当前已认证 Session。
     *
     * @param session 当前已认证 Session
     * @return 不包含响应数据的成功结果
     */
    @DeleteMapping
    @RequireSessionToken
    public @NotNull ApiResponse<Void> logout(@CurrentSession @NotNull Session session) {
        sessions.terminate(session, SessionTerminationReason.USER_REQUESTED);
        return ApiResponse.noContent();
    }

    /**
     * 使用当前不透明 Session Token 在线签发 access Token，不延长或轮换 backing Session。
     *
     * @param authorization 完整 Bearer Session Token
     * @return 当前认证模式的 Token 集合
     */
    @PostMapping("/tokens")
    @RequireSessionToken
    public @NotNull ApiResponse<SessionTokensView> refresh(
            @RequestHeader(name = org.springframework.http.HttpHeaders.AUTHORIZATION, required = false) @NoDefaultValidation @Nullable String authorization) {
        String sessionToken = BearerToken.parse(authorization)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.AUTHENTICATION_FAILED));
        return ApiResponse.ok(SessionTokensView.from(sessions.refreshTokens(sessionToken)));
    }
}
