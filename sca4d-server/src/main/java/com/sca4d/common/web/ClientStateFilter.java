package com.sca4d.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.StringJoiner;

/**
 * 前端本地状态响应头过滤器。
 */
public class ClientStateFilter extends OncePerRequestFilter {

    /**
     * 在业务过滤链完成后共用写出状态响应头并清理当前线程信号。
     *
     * <p>业务请求成功或失败都执行清理；响应已经提交时不再尝试修改响应头。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param filterChain 后续过滤器和 Controller 调用链
     * @throws ServletException 后续过滤链报告 Servlet 处理失败时抛出
     * @throws IOException 后续过滤链报告网络读写失败时抛出
     */
    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            ClientState.Change change = ClientState.currentOrNull();
            if (change != null && change.hasChange() && !response.isCommitted()) {
                if (change.isStale()) {
                    response.setHeader(ClientState.STALE_HEADER, "true");
                }
                if (!change.getRefreshTargets().isEmpty()) {
                    StringJoiner joiner = new StringJoiner(",");
                    change.getRefreshTargets().forEach(target -> joiner.add(target.getValue()));
                    response.setHeader(ClientState.REFRESH_HEADER, joiner.toString());
                }
                if (change.getVersion() != null && !change.getVersion().isBlank()) {
                    response.setHeader(ClientState.VERSION_HEADER, change.getVersion());
                }
            }
            ClientState.clear();
        }
    }
}
