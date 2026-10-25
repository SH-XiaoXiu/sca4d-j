package com.sca4d.core.spring.web;

import com.sca4d.core.api.audit.HttpCall;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallInvoker;
import com.sca4d.core.api.concurrent.IdempotencyKey;
import com.sca4d.core.api.data.DataValues;
import com.sca4d.core.spring.call.CallHolder;
import com.sca4d.core.spring.config.WebRequestProperties;
import com.sca4d.core.spring.gateway.TrustedGatewayRequest;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/** 为 HTTP 请求建立和清理 {@link Call}，在同步或异步请求完成时记录访问结果。 */
public class HttpCallFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(HttpCallFilter.class);

    private static final String CALL_ID_MDC = "callId";

    private static final Pattern CALL_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private final @Nullable CallInvoker invoker;

    private final @NotNull Clock clock;

    private final @NotNull LongSupplier nanoTime;

    private final long slowRequestThresholdNanos;

    /** @param clock 创建调用开始时间使用的时钟；慢请求计时使用单调时钟 */
    public HttpCallFilter(@NotNull Clock clock) {
        this(clock, null);
    }

    /** 创建使用默认慢请求阈值的请求过滤器。 */
    public HttpCallFilter(@NotNull Clock clock, @Nullable CallInvoker invoker) {
        this(clock, invoker, new WebRequestProperties());
    }

    /**
     * @param clock 创建 Call 开始时间使用的时钟
     * @param invoker 可选的调用执行器
     * @param properties 启动时绑定的全服务请求配置
     */
    public HttpCallFilter(
            @NotNull Clock clock,
            @Nullable CallInvoker invoker,
            @NotNull WebRequestProperties properties) {
        this(clock, invoker, properties, System::nanoTime);
    }

    /** 同包验证可提供可控的单调时钟，不使用 sleep 等待阈值。 */
    HttpCallFilter(
            @NotNull Clock clock,
            @Nullable CallInvoker invoker,
            @NotNull WebRequestProperties properties,
            @NotNull LongSupplier nanoTime) {
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.invoker = invoker;
        this.nanoTime = Objects.requireNonNull(nanoTime, "单调时钟不能为空");
        this.slowRequestThresholdNanos = Objects.requireNonNull(properties, "HTTP 请求配置不能为空")
                .getSlowRequestThreshold()
                .toNanos();
    }

    /**
     * 为本次请求建立 Call：请求头 X-Call-Id 符合 {@code [A-Za-z0-9._-]{1,128}} 时沿用，否则生成 UUID；
     * 随后把 callId 写入响应头和 MDC，并把 CallHolder 绑定到当前线程。
     * 无论同步还是异步启动，finally 都会清理 CallHolder 与 MDC；未启动异步时在 finally 中记录请求结果，已启动异步时由 AsyncListener 在完成事件中记录。
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        long startedNanos = nanoTime.getAsLong();
        String requestedCallId = request.getHeader(HttpConstants.CALL_ID_HEADER);
        String callId = requestedCallId != null && CALL_ID_PATTERN.matcher(requestedCallId).matches()
                ? requestedCallId
                : UUID.randomUUID().toString();
        HttpCall http = new HttpCall(
                request.getMethod(),
                TrustedGatewayRequest.externalPath(request),
                TrustedGatewayRequest.clientIp(request),
                request.getHeader(HttpHeaders.USER_AGENT));
        DataValues.Builder inputs = DataValues.builder().put(HttpCall.KEY, http);
        String idempotencyKey = request.getHeader(HttpConstants.IDEMPOTENCY_KEY_HEADER);
        if (idempotencyKey != null)
            inputs.put(IdempotencyKey.INPUT, idempotencyKey);
        Call call = new Call(invoker, callId, clock.instant(), inputs.build(), null);
        RequestTiming timing = new RequestTiming(callId, http.method(), http.path(), startedNanos, response);
        response.setHeader(HttpConstants.CALL_ID_HEADER, callId);
        try {
            CallHolder.set(call);
            MDC.put(CALL_ID_MDC, callId);
            // 在 startAsync 返回给调用方前登记监听，覆盖链内直接 complete 的请求。
            filterChain.doFilter(new TimedRequest(request, timing), response);
        } catch (IOException | ServletException | RuntimeException | Error failure) {
            timing.failed = true;
            throw failure;
        } finally {
            try {
                if (!timing.asyncStarted)
                    timing.complete(response);
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

    /** 在任一种异步启动方式返回前登记完成监听，不改变业务异步结果。 */
    private static class TimedRequest extends HttpServletRequestWrapper {
        private final @NotNull RequestTiming timing;

        private TimedRequest(@NotNull HttpServletRequest request, @NotNull RequestTiming timing) {
            super(request);
            this.timing = timing;
        }

        @Override
        public @NotNull AsyncContext startAsync() {
            AsyncContext context = super.startAsync();
            timing.startedAsync(context);
            return context;
        }

        @Override
        public @NotNull AsyncContext startAsync(ServletRequest request, ServletResponse response) {
            AsyncContext context = super.startAsync(request, response);
            timing.startedAsync(context);
            return context;
        }
    }

    /** 保存一次 HTTP 请求的计时快照，并跨异步周期只输出一次最终慢请求日志。 */
    private class RequestTiming implements AsyncListener {
        private final @NotNull HttpServletResponse initialResponse;

        private final @NotNull String callId;

        private final @NotNull String method;

        private final @NotNull String path;

        private final long startedNanos;

        private final @NotNull AtomicBoolean completed = new AtomicBoolean();

        private volatile boolean asyncStarted;

        private volatile boolean failed;

        private @NotNull Map<String, String> loggingContext = Map.of();

        private RequestTiming(
                @NotNull String callId,
                @NotNull String method,
                @NotNull String path,
                long startedNanos,
                @NotNull HttpServletResponse initialResponse) {
            this.callId = callId;
            this.method = method;
            this.path = path;
            this.startedNanos = startedNanos;
            this.initialResponse = initialResponse;
        }

        private void startedAsync(AsyncContext context) {
            if (asyncStarted)
                return;
            Map<String, String> captured = MDC.getCopyOfContextMap();
            loggingContext = captured == null ? Map.of() : Map.copyOf(captured);
            asyncStarted = true;
            register(context);
        }

        private void register(AsyncContext context) {
            try {
                context.addListener(this, context.getRequest(), context.getResponse());
            } catch (IllegalStateException alreadyCompleted) {
                // 容器在登记前完成时补记一次；正常监听回调由 completed 防止重复。
                complete(initialResponse);
            }
        }

        private void complete(HttpServletResponse response) {
            if (!completed.compareAndSet(false, true))
                return;
            long durationNanos = nanoTime.getAsLong() - startedNanos;
            String contentType = response.getContentType();
            // SSE 连接和已升级的 WebSocket 连接会长期保持，不输出普通 HTTP 访问日志。
            if (response.getStatus() == 101
                    || (contentType != null
                            && contentType.split(";", 2)[0].trim().equalsIgnoreCase("text/event-stream")))
                return;
            Map<String, String> previousContext = MDC.getCopyOfContextMap();
            try {
                if (asyncStarted)
                    MDC.setContextMap(loggingContext);
                MDC.put(CALL_ID_MDC, callId);
                long durationMs = TimeUnit.NANOSECONDS.toMillis(durationNanos);
                if (durationNanos >= slowRequestThresholdNanos) {
                    LOG.warn(
                            "慢 HTTP 请求 | callId={} method={} path={} status={} durationMs={} thresholdMs={} failed={}",
                            callId,
                            method,
                            path,
                            response.getStatus(),
                            durationMs,
                            TimeUnit.NANOSECONDS.toMillis(slowRequestThresholdNanos),
                            failed);
                } else {
                    LOG.info(
                            "HTTP 请求完成 | callId={} method={} path={} status={} durationMs={} failed={}",
                            callId,
                            method,
                            path,
                            response.getStatus(),
                            durationMs,
                            failed);
                }
            } finally {
                if (previousContext == null)
                    MDC.clear();
                else
                    MDC.setContextMap(previousContext);
            }
        }

        @Override
        public void onComplete(AsyncEvent event) {
            ServletResponse supplied = event.getSuppliedResponse();
            complete(supplied instanceof HttpServletResponse httpResponse ? httpResponse : initialResponse);
        }

        @Override
        public void onTimeout(AsyncEvent event) {
            // 超时处理器仍可写入响应，等待 onComplete 读取最终状态。
        }

        @Override
        public void onError(AsyncEvent event) {
            failed = true;
        }

        @Override
        public void onStartAsync(AsyncEvent event) {
            register(event.getAsyncContext());
        }
    }
}
