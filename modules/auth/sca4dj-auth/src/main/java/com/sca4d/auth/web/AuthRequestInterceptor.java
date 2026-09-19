package com.sca4d.auth.web;

import com.sca4d.auth.session.SessionService;
import com.sca4d.core.api.annotation.AnonymousAccess;
import com.sca4d.core.api.authentication.RequireSessionToken;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.spring.call.CallHandlerInterceptor;
import com.sca4d.core.spring.web.BearerToken;
import com.sca4d.core.spring.web.clientstate.ClientState;
import com.sca4d.core.spring.web.clientstate.ClientStateTarget;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.web.method.HandlerMethod;

import java.util.Objects;

/** 按 Controller 注解恢复 Session，并在参数绑定前拒绝未声明匿名访问的未认证请求。 */
public final class AuthRequestInterceptor extends CallHandlerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(AuthRequestInterceptor.class);

    private static final ClientStateTarget AUTHENTICATION_SESSION = ClientStateTarget.of("authentication.session");

    private final SessionService sessions;

    /**
     * 创建认证请求拦截器。
     *
     * @param sessions 按 Token 类型恢复 Session 的服务
     */
    public AuthRequestInterceptor(@NotNull SessionService sessions) {
        this.sessions = Objects.requireNonNull(sessions, "Session 服务不能为空");
    }

    /**
     * 在 Controller 执行前按 {@link RequireSessionToken} 选择唯一 Token 类型并绑定 Session。
     * 未声明 {@link AnonymousAccess} 且没有恢复有效 Session 时，登记
     * {@code authentication.session} 客户端状态刷新目标并拒绝请求。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param handler Spring MVC 已选择的 Handler
     * @param call 当前请求唯一的调用
     * @return 允许匿名访问或已经恢复有效 Session 时为 {@code true}
     * @throws BusinessException 受保护 Handler 没有有效 Session 时抛出
     */
    @Override
    protected boolean preHandleWithCall(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull Object handler,
            @NotNull Call call) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        boolean requireSessionToken = AnnotatedElementUtils.hasAnnotation(
                handlerMethod.getMethod(),
                RequireSessionToken.class)
                || AnnotatedElementUtils.hasAnnotation(
                        handlerMethod.getBeanType(),
                        RequireSessionToken.class);
        BearerToken.parse(request.getHeader(HttpHeaders.AUTHORIZATION))
                .flatMap(token -> sessions.currentSession(token, requireSessionToken))
                .ifPresent(call::authenticate);
        boolean anonymous = AnnotatedElementUtils.hasAnnotation(
                handlerMethod.getMethod(),
                AnonymousAccess.class)
                || AnnotatedElementUtils.hasAnnotation(
                        handlerMethod.getBeanType(),
                        AnonymousAccess.class);
        if (!anonymous && !call.isAuthenticationAssessed()) {
            ClientState.refresh(AUTHENTICATION_SESSION);
            LOG.trace(
                    "HTTP 请求缺少有效认证 | handler={} requireSessionToken={}",
                    handlerMethod.getMethod().getName(),
                    requireSessionToken);
            throw new BusinessException(CoreErrorCode.AUTHENTICATION_REQUIRED);
        }
        LOG.trace(
                "HTTP 认证要求已检查 | handler={} requireSessionToken={} anonymous={} authenticated={}",
                handlerMethod.getMethod().getName(),
                requireSessionToken,
                anonymous,
                call.isAuthenticationAssessed());
        return true;
    }
}
