package com.sca4d.common.auth.web;

import com.sca4d.common.permission.AccessPolicy;
import com.sca4d.common.request.CurrentRequestHolder;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 全局认证拦截器。
 *
 * <p>默认所有 Controller 接口要求登录；匿名接口必须使用 {@code @AnonymousAccess} 标注。
 */
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull Object handler) {
        // ASYNC 和 ERROR 都是原始请求完成后的容器续派，不会再次经过默认 OncePerRequestFilter。
        // 初始 REQUEST 已完成认证；续派只负责写出或结束响应，不能重新读取已清理的请求态。
        if (request.getDispatcherType() == DispatcherType.ASYNC
                || request.getDispatcherType() == DispatcherType.ERROR) {
            return true;
        }
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        AccessPolicy policy = AccessPolicy.resolve(
                handlerMethod.getMethod(), handlerMethod.getBeanType());
        if (!policy.isAnonymous()) {
            CurrentRequestHolder.current().requireSubject();
        }
        return true;
    }
}
