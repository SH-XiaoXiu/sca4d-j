package com.sca4d.app.server.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.authentication.Session;
import com.sca4d.web.realtime.sse.WebRealtimeSseConnector;
import com.sca4d.web.realtime.ticket.WebRealtimeTicketService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 验证 Realtime HTTP 路由设置流式响应所需的缓存和代理行为。 */
class RealtimeControllerTest {

    @Test
    @DisplayName("SSE 连接禁止缓存和 Nginx 响应缓冲")
    void disablesCachingAndProxyBuffering() {
        WebRealtimeSseConnector connections = mock(WebRealtimeSseConnector.class);
        Session session = mock(Session.class);
        SseEmitter emitter = mock(SseEmitter.class);
        when(connections.connect(session)).thenReturn(emitter);
        RealtimeController controller = new RealtimeController(
                connections,
                mock(WebRealtimeTicketService.class));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(controller.connect(session, response)).isSameAs(emitter);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-cache, no-transform");
        assertThat(response.getHeader("X-Accel-Buffering")).isEqualTo("no");
    }
}
