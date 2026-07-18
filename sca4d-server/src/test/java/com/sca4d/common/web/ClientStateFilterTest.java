package com.sca4d.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.authentication.AuthenticationClientStateTargets;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

/** 验证请求结束时把客户端刷新标记写入响应头并清理线程状态。 */
class ClientStateFilterTest {

    @Test
    void filterShouldWriteStateHeadersAndClearContext() throws ServletException, IOException {
        ClientStateFilter filter = new ClientStateFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/demo");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new StateChangingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(ClientState.STALE_HEADER)).isEqualTo("true");
        assertThat(response.getHeader(ClientState.REFRESH_HEADER)).isEqualTo("me,operation-logs");
        assertThat(response.getHeader(ClientState.VERSION_HEADER)).isEqualTo("v1");
        assertThat(ClientState.currentOrNull()).isNull();
    }

    @Test
    void filterShouldWriteStateHeadersWhenChainThrows() {
        ClientStateFilter filter = new ClientStateFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/demo");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new ThrowingStateChangingFilterChain();

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(ServletException.class)
                .hasMessage("handled by mvc");

        assertThat(response.getHeader(ClientState.STALE_HEADER)).isNull();
        assertThat(response.getHeader(ClientState.REFRESH_HEADER)).isEqualTo("me");
        assertThat(ClientState.currentOrNull()).isNull();
    }

    @Test
    void refreshShouldOnlyRegisterNonBlankExactTarget() {
        ClientState.refresh(AuthenticationClientStateTargets.ME);

        assertThat(ClientState.currentOrNull()).isNotNull();
        assertThat(ClientState.currentOrNull().isStale()).isFalse();
        assertThat(ClientState.currentOrNull().getRefreshTargets())
                .containsExactly(AuthenticationClientStateTargets.ME);
        ClientState.clear();
    }

    /** 在请求处理中写入客户端刷新标记的过滤器链。 */
    private static class StateChangingFilterChain extends MockFilterChain {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response)
                throws IOException, ServletException {
            ClientState.stale();
            ClientState.refresh(AuthenticationClientStateTargets.ME);
            ClientState.refresh(ClientStateTarget.of("operation-logs"));
            ClientState.version("v1");
            super.doFilter(request, response);
        }
    }

    /** 写入刷新标记后抛出异常的过滤器链。 */
    private static class ThrowingStateChangingFilterChain extends MockFilterChain {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response)
                throws ServletException {
            ClientState.refresh(AuthenticationClientStateTargets.ME);
            throw new ServletException("handled by mvc");
        }
    }
}
