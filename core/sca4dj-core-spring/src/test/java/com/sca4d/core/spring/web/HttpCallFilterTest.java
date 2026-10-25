package com.sca4d.core.spring.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sca4d.core.api.setting.ConfiguredConstants;
import com.sca4d.core.spring.call.CallHolder;
import com.sca4d.core.spring.config.WebRequestProperties;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** 用可控单调时钟验证请求日志、慢请求阈值、异步完成和请求上下文清理。 */
class HttpCallFilterTest {
    private final AtomicLong nanos = new AtomicLong();

    private final Logger logger = (Logger) LoggerFactory.getLogger(HttpCallFilter.class);

    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/resources");

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private Level previousLevel;

    private ListAppender<ILoggingEvent> events;

    private HttpCallFilter filter;

    @BeforeAll
    static void initializeConstants() {
        ConfiguredConstants.initialize(HttpConstants.class, ignored -> null);
    }

    @BeforeEach
    void captureLogs() {
        request.addHeader(HttpConstants.CALL_ID_HEADER, "slow-test-call");
        request.setAsyncSupported(true);
        filter = new HttpCallFilter(
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                null,
                new WebRequestProperties(),
                nanos::get);
        previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        events = new ListAppender<>() {
            @Override
            protected void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        };
        events.setContext(logger.getLoggerContext());
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void clearContext() {
        logger.detachAppender(events);
        events.stop();
        logger.setLevel(previousLevel);
        MDC.clear();
        CallHolder.clear();
    }

    @Test
    void logsNormalRequestsAtInfoWithoutSensitiveMetadata() throws Exception {
        request.setRequestURI("/resources?token=do-not-log");
        request.setQueryString("password=do-not-log");
        request.addHeader("Authorization", "Bearer do-not-log");
        filter.doFilter(request, response, (req, res) -> nanos.set(Duration.ofMillis(199).toNanos()));
        assertThat(events.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).contains(
                    "HTTP 请求完成",
                    "callId=slow-test-call",
                    "method=GET",
                    "path=/resources ",
                    "status=200",
                    "durationMs=199",
                    "failed=false").doesNotContain("do-not-log", "token", "password");
        });
        assertThat(CallHolder.currentOrNull()).isNull();
        assertThat(MDC.get("callId")).isNull();
    }

    @Test
    void logsAtThresholdWithSafeRequestMetadataAndNoStackTrace() throws Exception {
        request.setRequestURI("/resources?token=do-not-log");
        request.setQueryString("password=do-not-log");
        request.addHeader("Authorization", "Bearer do-not-log");
        filter.doFilter(request, response, (req, res) -> {
            response.setStatus(201);
            nanos.set(Duration.ofMillis(200).toNanos());
        });
        assertThat(events.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains(
                    "callId=slow-test-call",
                    "method=GET",
                    "path=/resources ",
                    "status=201",
                    "durationMs=200",
                    "thresholdMs=200").doesNotContain("do-not-log", "token", "password");
            assertThat(event.getMDCPropertyMap()).containsEntry("callId", "slow-test-call");
            assertThat(event.getThrowableProxy()).isNull();
        });
        assertThat(response.getHeader(HttpConstants.CALL_ID_HEADER)).isEqualTo("slow-test-call");
    }

    @Test
    void usesConfiguredThresholdAndMonotonicTimeInsteadOfWallClock() throws Exception {
        WebRequestProperties properties = new WebRequestProperties();
        properties.setSlowRequestThreshold(Duration.ofMillis(750));
        filter = new HttpCallFilter(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), null, properties, nanos::get);
        filter.doFilter(request, response, (req, res) -> nanos.set(Duration.ofMillis(700).toNanos()));
        assertThat(events.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).contains("durationMs=700", "failed=false");
        });
        filter.doFilter(request, response, (req, res) -> nanos.addAndGet(Duration.ofMillis(750).toNanos()));
        assertThat(events.list).hasSize(2);
        assertThat(events.list.get(1)).satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("durationMs=750", "thresholdMs=750");
        });
    }

    @Test
    void logsSlowFailuresWithoutReplacingTheExceptionOrLeakingContext() {
        ServletException failure = new ServletException("private failure details");
        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            nanos.set(Duration.ofMillis(300).toNanos());
            throw failure;
        })).isSameAs(failure);
        assertThat(events.list).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).contains("failed=true").doesNotContain("private failure details");
            assertThat(event.getThrowableProxy()).isNull();
        });
        assertThat(CallHolder.currentOrNull()).isNull();
        assertThat(MDC.get("callId")).isNull();
    }

    @Test
    void waitsForAsyncCompletionAndRestoresCompletionThreadMdc() throws Exception {
        AtomicReference<AsyncContext> async = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> {
            MDC.put("principalId", "request-principal");
            async.set(req.startAsync(req, res));
            nanos.set(Duration.ofMillis(250).toNanos());
        });
        assertThat(events.list).isEmpty();
        assertThat(CallHolder.currentOrNull()).isNull();
        assertThat(MDC.get("callId")).isNull();
        response.setStatus(202);
        nanos.set(Duration.ofMillis(400).toNanos());
        MDC.put("callId", "completion-thread-call");
        MDC.put("principalId", "completion-thread-principal");
        async.get().complete();
        assertThat(events.list).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).contains("status=202", "durationMs=400");
            assertThat(event.getMDCPropertyMap()).containsEntry("callId", "slow-test-call")
                    .containsEntry("principalId", "request-principal");
        });
        assertThat(MDC.get("callId")).isEqualTo("completion-thread-call");
        assertThat(MDC.get("principalId")).isEqualTo("completion-thread-principal");
    }

    @Test
    void capturesAsyncCompletionBeforeTheFilterChainReturnsOnlyOnce() throws Exception {
        filter.doFilter(request, response, (req, res) -> {
            AsyncContext async = req.startAsync(req, res);
            nanos.set(Duration.ofMillis(250).toNanos());
            async.complete();
        });
        assertThat(events.list).hasSize(1);
        assertThat(CallHolder.currentOrNull()).isNull();
    }

    @Test
    void reregistersAcrossAsyncCyclesAndWaitsForFinalStatusAfterTimeout() throws Exception {
        AtomicReference<AsyncContext> async = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> async.set(req.startAsync(req, res)));
        AsyncListener listener = ((MockAsyncContext) async.get()).getListeners().getFirst();
        MockAsyncContext next = new MockAsyncContext(request, response);
        listener.onStartAsync(new AsyncEvent(next, request, response));
        nanos.set(Duration.ofMillis(500).toNanos());
        AsyncEvent finished = new AsyncEvent(next, request, response);
        listener.onTimeout(finished);
        assertThat(events.list).isEmpty();
        response.setStatus(503);
        next.complete();
        listener.onComplete(finished);
        assertThat(events.list).singleElement()
                .satisfies(
                        event -> assertThat(event.getFormattedMessage())
                                .contains("status=503", "durationMs=500"));
    }

    @Test
    void logsAsyncErrorsAtCompletionWithoutRepeatingTheirStack() throws Exception {
        AtomicReference<AsyncContext> async = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> async.set(req.startAsync(req, res)));
        AsyncListener listener = ((MockAsyncContext) async.get()).getListeners().getFirst();
        nanos.set(Duration.ofMillis(350).toNanos());
        listener.onError(new AsyncEvent(async.get(), request, response, new IllegalStateException("private details")));
        assertThat(events.list).isEmpty();
        response.setStatus(500);
        async.get().complete();
        assertThat(events.list).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).contains("status=500", "failed=true", "durationMs=350")
                    .doesNotContain("private details");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void excludesActualEventStreamsAndUpgradedConnections() throws Exception {
        filter.doFilter(request, response, (req, res) -> {
            response.setContentType("text/event-stream;charset=UTF-8");
            AsyncContext async = req.startAsync(req, res);
            nanos.set(Duration.ofHours(1).toNanos());
            async.complete();
        });
        assertThat(events.list).isEmpty();
        MockHttpServletResponse upgraded = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/socket"), upgraded, (req, res) -> {
            upgraded.setStatus(101);
            nanos.addAndGet(Duration.ofSeconds(1).toNanos());
        });
        assertThat(events.list).isEmpty();
    }

    @Test
    void doesNotLetAcceptHeaderSuppressSlowErrors() throws Exception {
        request.addHeader("Accept", "text/event-stream");
        filter.doFilter(request, response, (req, res) -> {
            response.setStatus(500);
            response.setContentType("application/json");
            nanos.set(Duration.ofMillis(250).toNanos());
        });
        assertThat(events.list).singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains("status=500"));
    }

    @Test
    void observesNoArgumentAsyncStart() throws Exception {
        // Mock 请求需要先关联响应，Servlet 容器在真实请求中自动提供此响应。
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/resources") {
            @Override
            public AsyncContext startAsync() {
                return super.startAsync(this, response);
            }
        };
        servletRequest.setAsyncSupported(true);
        AtomicReference<AsyncContext> async = new AtomicReference<>();
        filter.doFilter(servletRequest, response, (req, res) -> async.set(((HttpServletRequest) req).startAsync()));
        nanos.set(Duration.ofMillis(350).toNanos());
        async.get().complete();
        assertThat(events.list).singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains("durationMs=350"));
    }
}
