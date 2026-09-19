package com.sca4d.core.spring.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.core.spring.call.CallHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** 验证身份 MDC 拦截器在请求完成后清理身份字段。 */
class IdentityMdcInterceptorTest {

    @AfterEach
    void clearContext() {
        CallHolder.clear();
        MDC.clear();
    }

    @Test
    @DisplayName("Clears Identity Fields After Request Completion")
    void clearsIdentityFieldsAfterRequestCompletion() {
        IdentityMdcInterceptor interceptor = new IdentityMdcInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/events");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Object handler = new Object();

        MDC.put("sessionId", "stale-session");
        MDC.put("subjectId", "stale-subject");
        MDC.put("accountId", "stale-account");
        MDC.put("principalId", "stale-principal");
        interceptor.afterCompletion(request, response, handler, null);

        assertThat(MDC.get("sessionId")).isNull();
        assertThat(MDC.get("subjectId")).isNull();
        assertThat(MDC.get("accountId")).isNull();
        assertThat(MDC.get("principalId")).isNull();
    }
}
