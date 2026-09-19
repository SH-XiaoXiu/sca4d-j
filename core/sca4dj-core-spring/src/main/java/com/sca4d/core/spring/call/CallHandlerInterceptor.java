package com.sca4d.core.spring.call;

import com.sca4d.core.api.call.Call;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.HandlerInterceptor;

/** 只在当前线程已经建立 `Call` 时执行请求阶段处理，并跳过 Spring MVC 异步分派。 */
public abstract class CallHandlerInterceptor implements HandlerInterceptor {

    /** 创建依赖当前 `Call` 的 Spring MVC 拦截器。 */
    protected CallHandlerInterceptor() {
    }

    /**
     * 处理初次请求，或者允许 Spring MVC 完成异步结果和断开连接分派。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param handler Spring MVC 已选择的 Handler
     * @return 异步分派或子类允许请求继续时为 {@code true}
     * @throws IllegalStateException 需要执行请求阶段处理但当前线程没有 `Call` 时抛出
     */
    @Override
    public final boolean preHandle(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull Object handler) {
        if (request.getDispatcherType() == DispatcherType.ASYNC) {
            return true;
        }
        return preHandleWithCall(request, response, handler, CallHolder.current());
    }

    /**
     * 使用当前线程已经建立的 `Call` 执行请求阶段处理。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param handler Spring MVC 已选择的 Handler
     * @param call 当前请求唯一的调用
     * @return 允许请求继续时为 {@code true}
     */
    protected abstract boolean preHandleWithCall(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull Object handler,
            @NotNull Call call);
}
