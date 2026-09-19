package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.core.api.connection.ConnectionId;
import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.event.EventType;
import com.sca4d.core.api.messaging.MessageDelivery;
import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.core.api.resource.ResourceReference;
import com.sca4d.core.api.webrealtime.WebRealtimeTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

/** 验证业务事件、消息投递和 Web Realtime 值类型的稳定字段及非法输入。 */
class MessageContractTest {

    private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");

    @Test
    @DisplayName("Keeps Event Contract Separate From Delivery Metadata")
    void keepsEventContractSeparateFromDeliveryMetadata() {
        Map<String, String> payload = Map.of("orderId", "42");
        @SuppressWarnings("unchecked")
        EventType<Map<String, String>> type = new EventType<>(
                "order.created",
                2,
                (Class<Map<String, String>>) (Class<?>) Map.class);
        Event<Map<String, String>> event = new Event<>(type, "order:42", NOW, payload);
        MessageDelivery<Map<String, String>> delivery = new MessageDelivery<>(
                new MessageId(42),
                event,
                "example-app",
                NOW.plusSeconds(1),
                true);

        assertEquals("order.created", event.type().code());
        assertEquals(2, event.type().protocolVersion());
        assertEquals(Map.class, event.type().payloadType());
        assertEquals("order:42", event.businessKey());
        assertEquals(NOW, event.occurredAt());
        assertSame(payload, event.payload());
        assertEquals(new MessageId(42), delivery.messageId());
        assertSame(event, delivery.event());
        assertEquals("example-app", delivery.sourceApplication());
        assertEquals(NOW.plusSeconds(1), delivery.publishedAt());
        assertTrue(delivery.redelivered());
        assertThrows(
                IllegalArgumentException.class,
                () -> new EventType<>("order.*", 1, Map.class));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EventType<>("order.created", 0, Map.class));
        EventType<String> wrongType = new EventType<>("order.created", 1, String.class);
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    Event<String> invalid = eventWithUncheckedPayload(wrongType, payload);
                    Object uncheckedPayload = invalid.payload();
                    assertSame(payload, uncheckedPayload);
                });
    }

    @Test
    @DisplayName("Builds Unambiguous Web Realtime Target Keys")
    void buildsUnambiguousWebRealtimeTargetKeys() {
        assertEquals("ACCOUNT:11", WebRealtimeTarget.account(11).key());
        assertEquals("SUBJECT:12", WebRealtimeTarget.subject(12).key());
        assertEquals("PRINCIPAL:13", WebRealtimeTarget.principal(13).key());
        assertEquals(
                "RESOURCE:6:tenant:2:14",
                WebRealtimeTarget.resource(new ResourceReference("tenant", "14")).key());
        assertNotEquals(
                WebRealtimeTarget.resource(new ResourceReference("a:b", "c")).key(),
                WebRealtimeTarget.resource(new ResourceReference("a", "b:c")).key());
        assertEquals("TOPIC:orders", WebRealtimeTarget.topic("orders").key());
        assertEquals("SESSION:session-15", WebRealtimeTarget.session(new SessionId("session-15")).key());
        assertEquals(
                "CONNECTION:connection-16",
                WebRealtimeTarget.connection(new ConnectionId("connection-16")).key());
    }

    /** 绕过编译期泛型检查，验证 Event 仍会拒绝与 EventType 不匹配的 payload。 */
    @SuppressWarnings("unchecked")
    private static <T> Event<T> eventWithUncheckedPayload(EventType<T> type, Object payload) {
        return new Event<>(type, null, NOW, (T) payload);
    }
}
