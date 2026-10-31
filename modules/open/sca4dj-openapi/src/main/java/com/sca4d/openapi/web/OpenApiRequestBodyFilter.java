package com.sca4d.openapi.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.IOException;

/** 为 OpenAPI 请求缓存请求体，供 HMAC 校验和 Controller 参数绑定共同读取。 */
public class OpenApiRequestBodyFilter extends OncePerRequestFilter {

    /** 读取 OpenAPI 请求体，并把可重复读取的请求包装传给后续过滤器。 */
    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > OpenApiCachedBodyHttpServletRequest.MAX_BODY_BYTES) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "OpenAPI 请求体过大");
            return;
        }
        OpenApiCachedBodyHttpServletRequest cached;
        try {
            cached = new OpenApiCachedBodyHttpServletRequest(request);
        } catch (MaxUploadSizeExceededException exception) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "OpenAPI 请求体过大");
            return;
        }
        filterChain.doFilter(cached, response);
    }

    /** 认证 Token 端点也需要表单绑定，因此所有 OpenAPI 路径都使用同一请求包装。 */
    @Override
    protected boolean shouldNotFilter(@NotNull HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith(OpenApiConstants.PATH_PREFIX + "/");
    }
}
