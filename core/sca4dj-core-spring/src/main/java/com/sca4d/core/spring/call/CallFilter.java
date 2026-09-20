package com.sca4d.core.spring.call;

import com.sca4d.core.api.audit.HttpCall;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.spring.gateway.TrustedGatewayRequest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** 为每次 HTTP 请求建立和清理 {@link Call}。 */
public final class CallFilter extends OncePerRequestFilter {

    /** 请求和响应使用的调用标识 Header。 */
    public static final String CALL_ID_HEADER = "X-Call-Id";

    private static final String CALL_ID_MDC = "callId";

    private static final Pattern CALL_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private final Clock clock;

    /**
     * 创建请求信息过滤器。
     *
     * @param clock 创建调用开始时间使用的时钟
     */
    public CallFilter(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        String requestedCallId = request.getHeader(CALL_ID_HEADER);
        String callId = requestedCallId != null && CALL_ID_PATTERN.matcher(requestedCallId).matches()
                ? requestedCallId
                : UUID.randomUUID().toString();
        Call call = new Call(
                callId,
                clock.instant(),
                new HttpCall(
                        request.getMethod(),
                        TrustedGatewayRequest.externalPath(request),
                        TrustedGatewayRequest.clientIp(request),
                        request.getHeader("User-Agent")));
        response.setHeader(CALL_ID_HEADER, callId);
        try {
            CallHolder.set(call);
            MDC.put(CALL_ID_MDC, callId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("sessionId");
            MDC.remove("principalId");
            MDC.remove("accountId");
            MDC.remove("subjectId");
            MDC.remove(CALL_ID_MDC);
            CallHolder.clear();
        }
    }
}
