package com.sca4d.core.spring.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/** 在每次 HTTP 请求结束后清理当前线程保存的版本前置条件。 */
public final class RequestVersionConditionFilter extends OncePerRequestFilter {

    private final RequestVersionConditions versionConditions;

    /**
     * 创建版本条件清理过滤器。
     *
     * @param versionConditions HTTP 请求版本条件入口
     */
    public RequestVersionConditionFilter(RequestVersionConditions versionConditions) {
        this.versionConditions = Objects.requireNonNull(versionConditions, "HTTP 请求版本条件入口不能为空");
    }

    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            versionConditions.clear();
        }
    }
}
