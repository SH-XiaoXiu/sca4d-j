package com.sca4d.web.realtime.stomp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.AuthenticationMethodCode;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.web.realtime.connection.BrowserEnvelope;
import com.sca4d.web.realtime.connection.ConnectionDelivery;
import com.sca4d.web.realtime.connection.LocalConnection;
import com.sca4d.web.realtime.connection.LocalConnectionRegistry;
import com.sca4d.web.realtime.connection.NodeTransport;
import com.sca4d.web.realtime.connection.RedisConnectionRegistry;
import com.sca4d.web.realtime.ticket.TicketHandshakeInterceptor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionConnectedEvent;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 验证 STOMP 连接不依赖 Spring Principal，并按 Spring Session 精确投递。 */
class StompConnectionsTest {

    @Test
    void registersTicketSessionAndDeliversToExactSpringSession() {
        Session session = session();
        RedisConnectionRegistry shared = mock(RedisConnectionRegistry.class);
        LocalConnectionRegistry local = mock(LocalConnectionRegistry.class);
        NodeTransport transport = mock(NodeTransport.class);
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        WebSocketSessions sockets = mock(WebSocketSessions.class);
        when(transport.nodeId()).thenReturn("node-1");
        StompConnections connections = new StompConnections(shared, local, transport, messaging, sockets);

        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.CONNECT_ACK);
        accessor.setSessionId("stomp-session-1");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TicketHandshakeInterceptor.SESSION_ATTRIBUTE, session);
        accessor.setSessionAttributes(attributes);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        connections.connected(new SessionConnectedEvent(this, message));

        ArgumentCaptor<LocalConnection> connectionCaptor = ArgumentCaptor.forClass(LocalConnection.class);
        verify(local).add(connectionCaptor.capture());
        LocalConnection connection = connectionCaptor.getValue();
        verify(shared).register(connection.id(), "node-1", session);
        assertThat(attributes.get(StompConnections.CONNECTION_ATTRIBUTE)).isEqualTo(connection.id());

        BrowserEnvelope envelope = new BrowserEnvelope(
                "invoice.changed",
                1,
                Instant.parse("2026-07-30T00:00:00Z"),
                JsonNodeFactory.instance.objectNode());
        connection.offer(new ConnectionDelivery(envelope, true));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(messaging).convertAndSendToUser(
                eq("stomp-session-1"),
                eq(WebRealtimeProtocol.USER_EVENT_BROKER_DESTINATION),
                same(envelope),
                headersCaptor.capture());
        assertThat(SimpMessageHeaderAccessor.getSessionId(headersCaptor.getValue()))
                .isEqualTo("stomp-session-1");
        assertThat(headersCaptor.getValue().get(StompConnections.CLOSE_AFTER_HEADER)).isEqualTo(true);
        verify(sockets, org.mockito.Mockito.never()).close(any(String.class));
    }

    /** 创建包含一个可选业务主体的有效 Session。 */
    private static Session session() {
        Instant issuedAt = Instant.parse("2026-07-30T00:00:00Z");
        Subject subject = new Subject(11, SubjectType.HUMAN);
        Account account = new Account(21, subject.subjectId(), new RealmKey("test"));
        Principal principal = () -> 31;
        return new Session(
                new SessionId("session-1"),
                subject,
                account,
                List.of(principal),
                List.of(),
                List.of(
                        new Authentication(
                                account.accountId(),
                                new AuthenticationMethodCode("test"),
                                Set.of(AuthenticationFactorKind.KNOWLEDGE),
                                issuedAt)),
                Map.of(principal.principalId(), List.of()),
                Map.of(),
                issuedAt,
                issuedAt.plusSeconds(3600));
    }
}
