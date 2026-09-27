package com.sca4d.openapi.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** 只为 OpenAPI 路由缓存请求体，供 MAC 校验和 Controller 参数绑定共同读取。 */
public final class OpenApiRequestBodyFilter extends OncePerRequestFilter {

    /** 允许缓存的单次请求体上限，避免 MAC 校验把无限输入读入内存。 */
    private static final int MAX_BODY_BYTES = 8 * 1024 * 1024;

    /** 在 OpenAPI 路径读取请求体并转交可重复读取的包装请求。 */
    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "OpenAPI 请求体过大");
            return;
        }
        filterChain.doFilter(new OpenApiCachedBodyHttpServletRequest(request), response);
    }

    /** 认证 Token 端点也需要表单绑定，因此所有 OpenAPI 路径都使用同一包装。 */
    @Override
    protected boolean shouldNotFilter(@NotNull HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/openapi/");
    }
}
