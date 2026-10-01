package com.sca4d.web.realtime.stomp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.concurrent.RateLimitDecision;
import com.sca4d.core.api.connection.ConnectionId;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.spring.call.CallHolder;
import com.sca4d.web.realtime.config.WebRealtimeProperties;
import com.sca4d.web.realtime.connection.BrowserEnvelope;
import com.sca4d.web.realtime.connection.BrowserWrite;
import com.sca4d.web.realtime.connection.LocalConnectionRegistry;
import com.sca4d.web.realtime.connection.NodeRouter;
import com.sca4d.web.realtime.connection.RedisConnectionRegistry;
import com.sca4d.web.realtime.ticket.TicketHandshakeInterceptor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.broker.BrokerAvailabilityEvent;
import org.springframework.mock.web.MockServletContext;
import org.springframework.stereotype.Controller;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.server.support.WebSocketHandlerMapping;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 使用真实 Spring STOMP 和隔离 RabbitMQ 验证两种 broker 模式的消息生命周期。 */
@Testcontainers
class StompRuntimeIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    @Container
    private static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4-alpine")
            .withPluginsEnabled("rabbitmq_stomp")
            .withExposedPorts(5672, 15672, 61613)
            .withAdminUser("sca4dj")
            .withAdminPassword("sca4dj");

    @ParameterizedTest
    @EnumSource(StompBrokerMode.class)
    void connectsSubscribesDispatchesAndClosesAfterInvalidation(StompBrokerMode mode) throws Exception {
        WebRealtimeProperties properties = new WebRealtimeProperties();
        properties.getStomp().setMode(mode);
        properties.getStomp().getRelay().setHost(RABBIT.getHost());
        properties.getStomp().getRelay().setPort(RABBIT.getMappedPort(61613));
        properties.getStomp().getRelay().setClientLogin("sca4dj");
        properties.getStomp().getRelay().setClientPasscode("sca4dj");
        properties.getStomp().getRelay().setSystemLogin("sca4dj");
        properties.getStomp().getRelay().setSystemPasscode("sca4dj");
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.addBeanFactoryPostProcessor(factory -> factory.registerSingleton("properties", properties));
            context.register(RuntimeConfiguration.class);
            context.refresh();
            ProbeController probe = context.getBean(ProbeController.class);
            assertThat(probe.brokerReady.await(10, TimeUnit.SECONDS)).isTrue();

            WebSocketHandlerMapping mapping = context
                    .getBean("stompWebSocketHandlerMapping", WebSocketHandlerMapping.class);
            WebSocketHttpRequestHandler requestHandler = (WebSocketHttpRequestHandler) mapping.getHandlerMap()
                    .get("/events/ws");
            WebSocketHandler handler = requestHandler.getWebSocketHandler();
            ArrayBlockingQueue<String> frames = new ArrayBlockingQueue<>(16);
            ArrayBlockingQueue<CloseStatus> closes = new ArrayBlockingQueue<>(2);
            WebSocketSession socket = mock(WebSocketSession.class);
            AtomicBoolean open = new AtomicBoolean(true);
            Map<String, Object> attributes = new ConcurrentHashMap<>();
            Principal principal = () -> 31;
            attributes.put(
                    TicketHandshakeInterceptor.SESSION_ATTRIBUTE,
                    new Session(
                            new SessionId("auth-session"),
                            new Subject(11, SubjectType.HUMAN),
                            new Account(21, 11, new RealmKey("test")),
                            List.of(principal),
                            List.of(),
                            List.of(
                                    new Authentication(
                                            21,
                                            TreeCode.of("auth.test"),
                                            Set.of(AuthenticationFactorKind.KNOWLEDGE),
                                            NOW)),
                            Map.of(31L, List.of()),
                            Map.of(),
                            NOW,
                            NOW.plusSeconds(3600)));
            attributes.put(TicketHandshakeInterceptor.IP_ATTRIBUTE, "127.0.0.1");
            when(socket.getId()).thenReturn("browser");
            when(socket.getAttributes()).thenReturn(attributes);
            when(socket.isOpen()).thenAnswer(ignored -> open.get());
            when(socket.getAcceptedProtocol()).thenReturn("v12.stomp");
            doAnswer(call -> {
                frames.add(((TextMessage) call.getArgument(0)).getPayload());
                return null;
            }).when(socket).sendMessage(any());
            doAnswer(call -> {
                CloseStatus status = call.getArgument(0);
                if (open.getAndSet(false)) {
                    handler.afterConnectionClosed(socket, status);
                    closes.add(status);
                }
                return null;
            }).when(socket).close(any(CloseStatus.class));
            handler.afterConnectionEstablished(socket);
            try {
                handler.handleMessage(
                        socket,
                        new TextMessage("CONNECT\naccept-version:1.2\nhost:/\nheart-beat:0,0\n\n\0"));
                assertThat(frames.poll(10, TimeUnit.SECONDS)).startsWith("CONNECTED\n");
                handler.handleMessage(
                        socket,
                        new TextMessage(
                                "SUBSCRIBE\nid:events\ndestination:/user/queue/events\nprincipal-id:31\nreceipt:subscribed\n\n\0"));
                // Relay 用 receipt 确认远端订阅；simple broker 的订阅与后续 SEND 由同一连接队列排序。
                if (mode == StompBrokerMode.BROKER_RELAY) {
                    assertThat(frames.poll(10, TimeUnit.SECONDS)).startsWith("RECEIPT\n");
                }
                handler.handleMessage(
                        socket,
                        new TextMessage(
                                "SEND\ndestination:/app/probe\nprincipal-id:31\ncontent-type:application/json\n\n{\"value\":1}\0"));
                handler.handleMessage(
                        socket,
                        new TextMessage(
                                "SEND\ndestination:/app/probe\nprincipal-id:31\ncontent-type:application/json\n\n{\"value\":2}\0"));
                assertThat(probe.received.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(probe.values).containsExactly(1, 2);
                assertThat(probe.calls).hasSize(2);
                assertThat(probe.calls.get(0)).isNotSameAs(probe.calls.get(1));
                assertThat(CallHolder.currentOrNull()).isNull();

                ConnectionId id = (ConnectionId) attributes.get(StompConnections.CONNECTION_ATTRIBUTE);
                BrowserEnvelope envelope = new BrowserEnvelope(
                        TreeCode.of("session.invalidated"),
                        1,
                        NOW,
                        JsonNodeFactory.instance.objectNode());
                context.getBean(LocalConnectionRegistry.class).deliver(id, new BrowserWrite(envelope, true));
                assertThat(frames.poll(10, TimeUnit.SECONDS)).startsWith("MESSAGE\n")
                        .contains("SESSION/INVALIDATED")
                        .doesNotContain(StompConnections.CLOSE_AFTER_HEADER);
                assertThat(closes.poll(10, TimeUnit.SECONDS)).isEqualTo(CloseStatus.POLICY_VIOLATION);
            } finally {
                if (open.get()) {
                    handler.afterConnectionClosed(socket, CloseStatus.NORMAL);
                }
            }
        }
    }

    /** 装配实际 STOMP 配置，Redis 和票据存储用隔离替身提供已认证会话。 */
    @Configuration(proxyBeanMethods = false)
    @Import(StompConfiguration.class)
    static class RuntimeConfiguration {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        WebSocketSessions sockets() {
            return new WebSocketSessions();
        }

        @Bean
        TicketHandshakeInterceptor ticketHandshakeInterceptor() {
            return mock(TicketHandshakeInterceptor.class);
        }

        @Bean
        RedisConnectionRegistry shared() {
            return mock(RedisConnectionRegistry.class);
        }

        @Bean
        LocalConnectionRegistry local(RedisConnectionRegistry shared) {
            return new LocalConnectionRegistry(shared);
        }

        @Bean
        StompConnections connections(
                RedisConnectionRegistry shared,
                LocalConnectionRegistry local,
                SimpMessagingTemplate template,
                WebSocketSessions sockets) {
            NodeRouter router = mock(NodeRouter.class);
            when(router.nodeId()).thenReturn("test-node");
            return new StompConnections(shared, local, router, template, sockets);
        }

        @Bean
        StompInboundInterceptor inbound(RedisConnectionRegistry shared) {
            return new StompInboundInterceptor(
                    new StompRateLimits((key, limit) -> RateLimitDecision.allow()),
                    shared,
                    List.of(),
                    Clock.fixed(NOW, ZoneOffset.UTC));
        }

        @Bean
        StompOutboundInterceptor outbound(WebSocketSessions sockets) {
            return new StompOutboundInterceptor(sockets);
        }

        @Bean(destroyMethod = "close")
        StompChannelDispatcher stompInboundDispatcher(WebRealtimeProperties properties, WebSocketSessions sockets) {
            return new StompChannelDispatcher("inbound", properties.getStomp(), sockets);
        }

        @Bean(destroyMethod = "close")
        StompChannelDispatcher stompOutboundDispatcher(WebRealtimeProperties properties, WebSocketSessions sockets) {
            return new StompChannelDispatcher("outbound", properties.getStomp(), sockets);
        }

        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }

    /** 通过实际 @MessageMapping 记录帧顺序和每帧的独立 Call。 */
    @Controller
    static class ProbeController {
        private final CountDownLatch brokerReady = new CountDownLatch(1);

        private final CountDownLatch received = new CountDownLatch(2);

        private final List<Integer> values = new CopyOnWriteArrayList<>();

        private final List<Call> calls = new CopyOnWriteArrayList<>();

        @EventListener
        public void brokerReady(BrokerAvailabilityEvent event) {
            if (event.isBrokerAvailable()) {
                brokerReady.countDown();
            }
        }

        @MessageMapping("/probe")
        public void receive(Map<String, Integer> payload) {
            calls.add(CallHolder.current());
            values.add(payload.get("value"));
            received.countDown();
        }
    }
}
