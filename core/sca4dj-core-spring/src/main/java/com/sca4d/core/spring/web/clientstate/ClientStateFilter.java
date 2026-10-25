package com.sca4d.core.spring.web.clientstate;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** 为无响应体和异常响应补充客户端状态响应头。 */
public class ClientStateFilter extends OncePerRequestFilter {

    public ClientStateFilter() {
    }

    /**
     * 执行后续过滤器链；响应尚未提交时把当前请求的状态刷新头写入响应，并在最后清理 ClientState。
     * 异常情况下同样在 finally 中写入和清理，异常本身继续向上抛出。
     */
    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            try {
                if (!response.isCommitted()) {
                    HttpHeaders headers = new HttpHeaders();
                    ClientStateHeaders.write(headers);
                    headers.forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
                }
            } finally {
                ClientState.clear();
            }
        }
    }
}
