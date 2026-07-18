package com.sca4d.common.realtime.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.sca4d.common.concurrent.ratelimit.RateLimitContextHolder;
import com.sca4d.common.realtime.RealtimeRoutes;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import com.sca4d.test.TestSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/** 验证 STOMP 帧进入和离开线程时设置并清理 CurrentRequest。 */
class StompRequestInterceptorTest {

    private final StompRequestInterceptor interceptor = new StompRequestInterceptor();

    @AfterEach
    void tearDown() {
        CurrentRequestHolder.clear();
        RateLimitContextHolder.clear();
    }

    @Test
    void shouldRestoreSharedThreadContextsForStompFrame() {
        CurrentRequest handshakeRequest = new CurrentRequest(
                "trace-1", "WS:HANDSHAKE", RealtimeRoutes.WS_ENDPOINT, "10.0.0.1", OffsetDateTime.now());
        handshakeRequest.setSession(TestSession.of(1L));
        handshakeRequest.setSubject(handshakeRequest.getSession().getSubject());
        handshakeRequest.setAccount(handshakeRequest.getSession().getAccount());

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(RealtimeHandshakeInterceptor.CURRENT_REQUEST_ATTRIBUTE, handshakeRequest);
        attributes.put(RealtimeHandshakeInterceptor.CLIENT_IP_ATTRIBUTE, "10.0.0.1");
        attributes.put(RealtimeHandshakeInterceptor.USER_AGENT_ATTRIBUTE, "JUnit");

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionId("sess-1");
        accessor.setDestination("/app/demo");
        accessor.setSessionAttributes(attributes);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        interceptor.beforeHandle(message, mock(MessageChannel.class), mock(MessageHandler.class));

        assertThat(CurrentRequestHolder.current().getTraceId()).isEqualTo("trace-1");
        assertThat(CurrentRequestHolder.current().getMethod()).isEqualTo("WS:SEND");
        assertThat(CurrentRequestHolder.current().getPath()).isEqualTo("/app/demo");
        assertThat(CurrentRequestHolder.current()).isNotSameAs(handshakeRequest);
        assertThat(CurrentRequestHolder.current().getSession())
                .isSameAs(handshakeRequest.getSession());
        assertThat(CurrentRequestHolder.current().getSubject())
                .isSameAs(handshakeRequest.getSubject());
        assertThat(CurrentRequestHolder.current().getAccount())
                .isSameAs(handshakeRequest.getAccount());
        assertThat(CurrentRequestHolder.current().authenticated()).isTrue();
        assertThat(CurrentRequestHolder.current().getPayload()).isEqualTo(new byte[0]);
        assertThat(RateLimitContextHolder.currentOrNull()).isNotNull();
        assertThat(RateLimitContextHolder.currentOrNull().getEndpoint()).isEqualTo("WS:SEND:/app/demo");
        assertThat(RateLimitContextHolder.currentOrNull().getConnectionId()).isEqualTo("sess-1");

        interceptor.afterMessageHandled(message, mock(MessageChannel.class), mock(MessageHandler.class), null);

        assertThat(CurrentRequestHolder.currentOrNull()).isNull();
        assertThat(RateLimitContextHolder.currentOrNull()).isNull();
    }

    @Test
    void shouldRejectHandshakeRequestWithoutTraceId() {
        CurrentRequest handshakeRequest = new CurrentRequest();
        handshakeRequest.setPath(RealtimeRoutes.WS_ENDPOINT);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(RealtimeHandshakeInterceptor.CURRENT_REQUEST_ATTRIBUTE, handshakeRequest);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/demo");
        accessor.setSessionAttributes(attributes);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.beforeHandle(
                message, mock(MessageChannel.class), mock(MessageHandler.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("WebSocket 握手请求缺少 traceId");
        assertThat(CurrentRequestHolder.currentOrNull()).isNull();
        assertThat(RateLimitContextHolder.currentOrNull()).isNull();
    }

    @Test
    void shouldRejectHandshakeRequestWithoutPath() {
        CurrentRequest handshakeRequest = new CurrentRequest();
        handshakeRequest.setTraceId("trace-1");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(RealtimeHandshakeInterceptor.CURRENT_REQUEST_ATTRIBUTE, handshakeRequest);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionAttributes(attributes);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.beforeHandle(
                message, mock(MessageChannel.class), mock(MessageHandler.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("WebSocket 握手请求缺少 path");
        assertThat(CurrentRequestHolder.currentOrNull()).isNull();
        assertThat(RateLimitContextHolder.currentOrNull()).isNull();
    }
}
