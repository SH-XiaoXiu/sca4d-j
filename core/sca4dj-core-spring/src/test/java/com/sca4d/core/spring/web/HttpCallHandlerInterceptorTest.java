package com.sca4d.core.spring.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.core.api.call.Call;
import com.sca4d.core.spring.call.CallHolder;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;

/** 验证 HttpCallHandlerInterceptor 在普通请求中把当前 Call 传给子类的 preHandleWithCall，在 Spring MVC ASYNC 重新分派时直接放行而不调用子类。 */
class HttpCallHandlerInterceptorTest {

    @AfterEach
    void clearCall() {
        CallHolder.clear();
    }

    @Test
    @DisplayName("Passes The Current Call To An Initial Request")
    void passesTheCurrentCallToAnInitialRequest() {
        Call call = new Call("call-1", Instant.parse("2026-07-28T00:00:00Z"));
        CallHolder.set(call);
        RecordingInterceptor interceptor = new RecordingInterceptor();

        assertThat(
                interceptor.preHandle(
                        new MockHttpServletRequest("GET", "/resource"),
                        new MockHttpServletResponse(),
                        new Object()))
                .isTrue();
        assertThat(interceptor.call).isSameAs(call);
    }

    @Test
    @DisplayName("Skips A Spring MVC Async Result Redispatch")
    void skipsASpringMvcAsyncResultRedispatch() {
        RecordingInterceptor interceptor = new RecordingInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/events");
        request.setDispatcherType(DispatcherType.ASYNC);

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(interceptor.call).isNull();
    }

    /** 记录基类传入的调用。 */
    private static class RecordingInterceptor extends HttpCallHandlerInterceptor {

        private Call call;

        @Override
        protected boolean preHandleWithCall(
                @NotNull HttpServletRequest request,
                @NotNull HttpServletResponse response,
                @NotNull Object handler,
                @NotNull Call call) {
            this.call = call;
            return true;
        }
    }
}
