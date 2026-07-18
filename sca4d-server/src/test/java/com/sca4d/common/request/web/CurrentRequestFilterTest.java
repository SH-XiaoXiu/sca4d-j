package com.sca4d.common.request.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.Authentication;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.Session;
import com.sca4d.common.auth.SessionSnapshot;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.permission.SessionPermission;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import com.sca4d.common.web.WebConstants;
import com.sca4d.config.properties.Sca4djRequestLogProperties;
import com.sca4d.test.TestSession;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** 验证 HTTP 过滤器建立 CurrentRequest、写入追踪标识并在结束时清理。 */
class CurrentRequestFilterTest {

    @Test
    void shouldInstallAuthenticatedRequestAndClearItAfterwards()
            throws ServletException, IOException {
        Session session = TestSession.of(7L);
        Sca4djRequestLogProperties properties = new Sca4djRequestLogProperties();
        properties.setEnabled(false);
        CurrentRequestFilter filter = new CurrentRequestFilter(new FakeAuthentication(session), properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/demo");
        request.addHeader(WebConstants.TRACE_ID_HEADER, "trace-1");
        request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("X-Scene", "test");
        request.addParameter("page", "1");
        request.addParameter("tenantId", "10");
        request.addParameter("tenantId", "11");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.request.getTraceId()).isEqualTo("trace-1");
        assertThat(chain.request.getPath()).isEqualTo("/demo");
        assertThat(chain.request.getClientIp()).isEqualTo("192.0.2.10");
        assertThat(chain.request.getSubject().getSubjectId()).isEqualTo(7L);
        assertThat(chain.request.getAccount().getAccountId()).isEqualTo(7L);
        assertThat(chain.request.getHeaders()).containsEntry("X-Scene", "test");
        assertThat(chain.request.getQueryParameters())
                .containsEntry("page", "1")
                .containsEntry("tenantId", List.of("10", "11"));
        assertThat(CurrentRequestHolder.currentOrNull()).isNull();
    }

    @Test
    void shouldReplaceUnsafeExternalTraceId() throws ServletException, IOException {
        Sca4djRequestLogProperties properties = new Sca4djRequestLogProperties();
        properties.setEnabled(false);
        CurrentRequestFilter filter = new CurrentRequestFilter(mock(Authentication.class), properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/demo");
        request.addHeader(WebConstants.TRACE_ID_HEADER, "invalid trace id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.request.getTraceId())
                .matches("[A-Za-z0-9._-]{1,128}")
                .isNotEqualTo("invalid trace id");
        assertThat(response.getHeader(WebConstants.TRACE_ID_HEADER)).isEqualTo(chain.request.getTraceId());
    }

    @Test
    void shouldRejectNegativeRequestLogThresholds() {
        Sca4djRequestLogProperties properties = new Sca4djRequestLogProperties();

        assertThatThrownBy(() -> properties.setSlowWarnThreshold(Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请求日志 WARN 阈值不能为负数");
        assertThatThrownBy(() -> properties.setSlowErrorThreshold(Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请求日志 ERROR 阈值不能为负数");
    }

    @Test
    void shouldClearThreadStateWhenAccessLogConfigurationFails() {
        Sca4djRequestLogProperties properties = mock(Sca4djRequestLogProperties.class);
        when(properties.isEnabled()).thenThrow(new IllegalStateException("log config failed"));
        CurrentRequestFilter filter = new CurrentRequestFilter(mock(Authentication.class), properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/demo");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Assertions.assertThatCode(
                () -> filter.doFilter(request, response, new MockFilterChain()))
                .doesNotThrowAnyException();
        assertThat(CurrentRequestHolder.currentOrNull()).isNull();
        assertThat(MDC.get("traceId")).isNull();
    }

    /** 在过滤器继续执行时记录 CurrentRequest 并返回固定响应。 */
    private static final class CapturingFilterChain extends MockFilterChain {

        private CurrentRequest request;

        @Override
        public void doFilter(
                ServletRequest request,
                ServletResponse response)
                throws IOException, ServletException {
            this.request = CurrentRequestHolder.current();
            super.doFilter(request, response);
        }
    }

    /** 为请求过滤器固定返回测试会话的认证实现。 */
    private static final class FakeAuthentication implements Authentication {

        private final Session session;

        private FakeAuthentication(Session session) {
            this.session = session;
        }

        @Override
        public @NotNull Session createSession(
                @NotNull Subject subject,
                @NotNull Account account,
                @NotNull List<Principal> principals,
                @NotNull Map<Long, SessionPermission> permissions) {
            return session;
        }

        @Override
        public @NotNull Session createSession(
                @NotNull Subject subject,
                @NotNull Account account,
                @NotNull List<Principal> principals,
                @NotNull Map<Long, SessionPermission> permissions,
                @Nullable String deviceId,
                @Nullable String terminalName) {
            return session;
        }

        @Override
        public void logout() {
            session.invalidate();
        }

        @Override
        public @Nullable Session currentSession() {
            return session;
        }

        @Override
        public void replaceSession(
                @NotNull Long accountId,
                @NotNull Subject subject,
                @NotNull Account account,
                @NotNull List<Principal> principals,
                @NotNull Map<Long, SessionPermission> permissions) {
        }

        @Override
        public @NotNull List<Long> onlineAccountIds() {
            return List.of(session.getAccount().getAccountId());
        }

        @Override
        public void endSessions(@NotNull Long accountId) {
        }

        @Override
        public @NotNull List<SessionSnapshot> sessions(@NotNull Long accountId) {
            return List.of();
        }

        @Override
        public void endSession(@NotNull Long accountId, @NotNull String sessionId) {
        }
    }
}
