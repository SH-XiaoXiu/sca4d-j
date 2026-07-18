package com.sca4d.common.request.web;

import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.Authentication;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.Session;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.core.TimeSupport;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import com.sca4d.common.request.TraceIds;
import com.sca4d.common.web.WebConstants;
import com.sca4d.config.properties.Sca4djRequestLogProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * HTTP CurrentRequest 初始化与访问日志过滤器。
 */
public class CurrentRequestFilter extends OncePerRequestFilter {

    private static final Logger accessLog = LoggerFactory.getLogger("com.sca4d.access");

    private static final String TRACE_ID = "traceId";
    private static final String SUBJECT_ID = "subjectId";
    private static final String ACCOUNT_ID = "accountId";
    private static final String PRINCIPAL_ID = "principalId";
    private static final String PRINCIPAL_TYPE = "principalType";

    private final Authentication authentication;
    private final Sca4djRequestLogProperties requestLogProperties;

    /**
     * 创建 HTTP CurrentRequest 过滤器。
     *
     * @param authentication 读取当前登录会话的认证实现
     * @param requestLogProperties 请求日志配置
     */
    public CurrentRequestFilter(
            @NotNull Authentication authentication,
            @NotNull Sca4djRequestLogProperties requestLogProperties) {
        this.authentication = authentication;
        this.requestLogProperties = requestLogProperties;
    }

    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain)
            throws ServletException, IOException {
        // 创建请求基础信息，并复用网关传入的 traceId。
        String traceId = TraceIds.resolve(request.getHeader(WebConstants.TRACE_ID_HEADER));
        response.setHeader(WebConstants.TRACE_ID_HEADER, traceId);
        long startNanos = System.nanoTime();
        CurrentRequest currentRequest = new CurrentRequest(
                traceId,
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr(),
                TimeSupport.now());
        currentRequest.setQueryParameters(HttpRequestFacts.queryParameters(request));
        currentRequest.setHeaders(HttpRequestFacts.headers(request));

        // 从 Sa-Token 读取当前 Session。未登录请求仍然拥有基础 CurrentRequest。
        Session session = authentication.currentSession();
        currentRequest.setSession(session);
        currentRequest.setSubject(session == null ? null : session.getSubject());
        currentRequest.setAccount(session == null ? null : session.getAccount());

        try {
            // 安装唯一请求态，供认证、权限、审计和业务公共组件复用。
            CurrentRequestHolder.set(currentRequest);
            MDC.put(TRACE_ID, traceId);
            filterChain.doFilter(request, response);
        } finally {
            try {
                // 在清理 ThreadLocal 前完成认证 MDC 与访问日志。
                writeAuthMdc(currentRequest);
                try {
                    writeAccessLog(request, response, currentRequest, startNanos);
                } catch (RuntimeException exception) {
                    // 访问日志是可观测性副作用，故障不能覆盖原业务异常或把成功响应改为失败。
                    accessLog.error("HTTP访问日志写入失败 traceId={}", traceId, exception);
                }
            } finally {
                // 日志配置或日志实现故障也不能把请求态留在线程池工作线程中。
                clearMdc();
                CurrentRequestHolder.clear();
            }
        }
    }

    /**
     * 输出 HTTP 访问日志。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param currentRequest 当前请求
     * @param startNanos 请求开始纳秒时间
     */
    private void writeAccessLog(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull CurrentRequest currentRequest,
            long startNanos) {
        if (!requestLogProperties.isEnabled()) {
            return;
        }
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        Subject subject = currentRequest.getSubject();
        Account account = currentRequest.getAccount();
        Principal principal = currentRequest.getPrincipal();
        Object[] args = {
                request.getMethod(),
                currentRequest.getPath(),
                response.getStatus(),
                durationMs,
                currentRequest.getTraceId(),
                subject == null ? null : subject.getSubjectId(),
                account == null ? null : account.getAccountId(),
                principal == null ? null : principal.getPrincipalType(),
                principal == null ? null : principal.getPrincipalId(),
                currentRequest.getClientIp(),
                currentRequest.getQueryParameters().isEmpty()
                        ? "-"
                        : currentRequest.getQueryParameters().keySet()
        };
        if (response.getStatus() >= 500
                || durationMs >= requestLogProperties.getSlowErrorThreshold().toMillis()) {
            accessLog.error(
                    "HTTP请求完成 method={} uri={} status={} durationMs={} traceId={} subjectId={} accountId={} principalType={} principalId={} clientIp={} query={}",
                    args);
            return;
        }
        if (durationMs >= requestLogProperties.getSlowWarnThreshold().toMillis()) {
            accessLog.warn(
                    "HTTP请求完成 method={} uri={} status={} durationMs={} traceId={} subjectId={} accountId={} principalType={} principalId={} clientIp={} query={}",
                    args);
            return;
        }
        accessLog.info(
                "HTTP请求完成 method={} uri={} status={} durationMs={} traceId={} subjectId={} accountId={} principalType={} principalId={} clientIp={} query={}",
                args);
    }

    /**
     * 写入认证相关 MDC 字段。
     *
     * @param currentRequest 当前请求
     */
    private void writeAuthMdc(@NotNull CurrentRequest currentRequest) {
        Subject subject = currentRequest.getSubject();
        Account account = currentRequest.getAccount();
        Principal principal = currentRequest.getPrincipal();
        if (subject != null) {
            MDC.put(SUBJECT_ID, String.valueOf(subject.getSubjectId()));
        }
        if (account != null) {
            MDC.put(ACCOUNT_ID, String.valueOf(account.getAccountId()));
        }
        if (principal != null) {
            MDC.put(PRINCIPAL_ID, String.valueOf(principal.getPrincipalId()));
            MDC.put(PRINCIPAL_TYPE, String.valueOf(principal.getPrincipalType()));
        }
    }

    /**
     * 清理当前请求写入的 MDC 字段。
     */
    private void clearMdc() {
        MDC.remove(TRACE_ID);
        MDC.remove(SUBJECT_ID);
        MDC.remove(ACCOUNT_ID);
        MDC.remove(PRINCIPAL_ID);
        MDC.remove(PRINCIPAL_TYPE);
    }

}
