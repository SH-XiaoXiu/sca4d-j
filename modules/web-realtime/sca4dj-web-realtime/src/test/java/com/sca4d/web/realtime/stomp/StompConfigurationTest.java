package com.sca4d.web.realtime.stomp;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.web.realtime.config.WebRealtimeProperties;
import com.sca4d.web.realtime.ticket.TicketHandshakeInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import java.time.Duration;

/** 验证 Web Realtime 交给 Spring STOMP 的端点顺序约束。 */
class StompConfigurationTest {

    @Test
    @DisplayName("同一 WebSocket 连接按接收顺序处理 STOMP 帧")
    void preservesStompReceiveOrderPerConnection() {
        WebRealtimeProperties properties = new WebRealtimeProperties();
        StompConfiguration configuration = new StompConfiguration(
                properties,
                mock(TicketHandshakeInterceptor.class),
                mock(WebSocketSessions.class),
                mock(StompInboundInterceptor.class),
                mock(StompOutboundInterceptor.class),
                mock(ObjectMapper.class));
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration endpoint = mock(StompWebSocketEndpointRegistration.class, RETURNS_SELF);
        when(registry.addEndpoint(WebRealtimeProtocol.WEBSOCKET_ENDPOINT)).thenReturn(endpoint);

        configuration.registerStompEndpoints(registry);

        verify(registry).setPreserveReceiveOrder(true);
    }

    @Test
    @DisplayName("拒绝会被毫秒 API 截断或溢出的时长")
    void rejectsDurationsThatCannotRetainMillisecondSemantics() {
        WebRealtimeProperties properties = new WebRealtimeProperties();

        assertThatThrownBy(() -> properties.setHeartbeatInterval(Duration.ofNanos(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能短于 1 毫秒");
        assertThatThrownBy(
                () -> properties.getStomp()
                        .getRelay()
                        .setStartupConnectTimeout(Duration.ofMillis((long) Integer.MAX_VALUE + 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能超过");
    }
}
