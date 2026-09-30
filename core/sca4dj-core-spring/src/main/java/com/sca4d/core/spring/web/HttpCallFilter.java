package com.sca4d.core.spring.web;

import com.sca4d.core.api.audit.HttpCall;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallInvoker;
import com.sca4d.core.api.concurrent.IdempotencyKey;
import com.sca4d.core.api.data.DataValues;
import com.sca4d.core.spring.call.CallHolder;
import com.sca4d.core.spring.gateway.TrustedGatewayRequest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** 为每次 HTTP 请求建立和清理 {@link Call}。 */
public class HttpCallFilter extends OncePerRequestFilter {

    private static final String CALL_ID_MDC = "callId";

    private static final Pattern CALL_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private final CallInvoker invoker;

    private final Clock clock;

    /**
     * 创建请求信息过滤器。
     *
     * @param clock 创建调用开始时间使用的时钟
     */
    public HttpCallFilter(Clock clock) {
        this(clock, null);
    }

    public HttpCallFilter(Clock clock, CallInvoker invoker) {
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.invoker = invoker;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String requestedCallId = request.getHeader(HttpConstants.CALL_ID_HEADER);
        String callId = requestedCallId != null && CALL_ID_PATTERN.matcher(requestedCallId).matches()
                ? requestedCallId
                : UUID.randomUUID().toString();
        DataValues.Builder inputs = DataValues.builder()
                .put(
                        HttpCall.KEY,
                        new HttpCall(
                                request.getMethod(),
                                TrustedGatewayRequest.externalPath(request),
                                TrustedGatewayRequest.clientIp(request),
                                request.getHeader("User-Agent")));
        String idempotencyKey = request.getHeader(HttpConstants.IDEMPOTENCY_KEY_HEADER);
        if (idempotencyKey != null) {
            inputs.put(IdempotencyKey.INPUT, idempotencyKey);
        }
        Call call = new Call(invoker, callId, clock.instant(), inputs.build(), null);
        response.setHeader(HttpConstants.CALL_ID_HEADER, callId);
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
