package com.sca4d.common.realtime.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.common.auth.Authentication;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.web.WebConstants;
import com.sca4d.test.TestSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.socket.WebSocketHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/** 验证 WebSocket 握手保存认证信息并拒绝未登录请求。 */
class RealtimeHandshakeInterceptorTest {

    @Test
    void shouldUseContainerRemoteAddressInsteadOfUntrustedForwardedHeader() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.currentSession()).thenReturn(TestSession.of(7L));
        RealtimeHandshakeInterceptor interceptor = new RealtimeHandshakeInterceptor(authentication);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/realtime/ws");
        servletRequest.setRemoteAddr("192.0.2.20");
        servletRequest.addHeader("X-Forwarded-For", "10.0.0.1");
        servletRequest.addHeader(WebConstants.TRACE_ID_HEADER, "invalid trace id");
        Map<String, Object> attributes = new LinkedHashMap<>();

        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class),
                attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes.get(RealtimeHandshakeInterceptor.CURRENT_REQUEST_ATTRIBUTE))
                .isInstanceOfSatisfying(CurrentRequest.class, request -> {
                    assertThat(request.getClientIp()).isEqualTo("192.0.2.20");
                    assertThat(request.getTraceId())
                            .matches("[A-Za-z0-9._-]{1,128}")
                            .isNotEqualTo("invalid trace id");
                });
        assertThat(attributes).containsEntry(RealtimeHandshakeInterceptor.CLIENT_IP_ATTRIBUTE, "192.0.2.20");
    }
}
