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
