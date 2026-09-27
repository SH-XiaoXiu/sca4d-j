package com.sca4d.web.realtime.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.core.api.authentication.SessionLifecycle;
import com.sca4d.core.api.connection.ConnectionId;
import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.event.EventType;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.core.api.isolation.AppName;
import com.sca4d.core.api.messaging.DispatchPolicy;
import com.sca4d.core.api.messaging.DispatchReceipt;
import com.sca4d.core.api.messaging.IncomingMessage;
import com.sca4d.core.api.messaging.MessageBus;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.Name;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.webrealtime.WebRealtimeMessage;
import com.sca4d.core.api.webrealtime.WebRealtimePublisher;
import com.sca4d.core.api.webrealtime.WebRealtimeTarget;
import com.sca4d.core.spring.redis.KeyRedisSerializer;
import com.sca4d.core.spring.redis.RedisBatches;
import com.sca4d.core.spring.redis.RedisHashTag;
import com.sca4d.core.spring.redis.RedisPipelineProperties;
import com.sca4d.core.spring.redis.RedisRateLimiter;
import com.sca4d.web.realtime.connection.BrowserWrite;
import com.sca4d.web.realtime.connection.DefaultWebRealtimePublisher;
import com.sca4d.web.realtime.connection.LocalConnection;
import com.sca4d.web.realtime.connection.LocalConnectionRegistry;
import com.sca4d.web.realtime.connection.RedisConnectionRegistry;
import com.sca4d.web.realtime.connection.WebRealtimeMessageCodec;
import com.sca4d.web.realtime.connection.WebRealtimeMessageHandler;
import com.sca4d.web.realtime.rabbit.NodeMessageCodec;
import com.sca4d.web.realtime.rabbit.RabbitNodeRouter;
import com.sca4d.web.realtime.stomp.StompConnections;
import com.sca4d.web.realtime.stomp.StompInboundInterceptor;
import com.sca4d.web.realtime.stomp.StompRateLimits;
import com.sca4d.web.realtime.stomp.WebRealtimeProtocol;
import com.sca4d.web.realtime.ticket.RedisTicketService;
import com.sca4d.web.realtime.ticket.TicketHandshakeInterceptor;
import com.sca4d.web.realtime.ticket.WebRealtimeTicket;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.serialization.Mapper;
import io.github.bucket4j.redis.redisson.Bucket4jRedisson;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 使用 Redis 和 RabbitMQ 验证共享连接索引、节点路由和一次性票据。
 */
@Testcontainers
@SuppressWarnings("resource")
class WebRealtimeInfrastructureIntegrationTest {

    static {
        AppName.publish("WEB_REALTIME_TEST");
    }

    private static final WebRealtimeProperties PROPERTIES = new WebRealtimeProperties();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    private static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4-alpine")
            .withAdminUser("sca4dj")
            .withAdminPassword("sca4dj");

    private static RedisTemplate<Key, String> redis;

    private static CachingConnectionFactory rabbitConnectionFactory;

    private static RabbitTemplate rabbit;

    private static RabbitAdmin rabbitAdmin;

    private static RedissonClient redisson;

    private static ProxyManager<String> rateLimitBuckets;

    private MutableClock clock;

    private RedisConnectionRegistry firstRegistry;

    private RedisConnectionRegistry secondRegistry;

    private LocalConnectionRegistry firstLocal;

    private LocalConnectionRegistry secondLocal;

    private RabbitNodeRouter firstNode;

    private RabbitNodeRouter secondNode;

    @BeforeAll
    static void connectInfrastructure() {
        PROPERTIES.setLeaseDuration(Duration.ofSeconds(5));
        PROPERTIES.setTicketTtl(Duration.ofSeconds(10));

        RedisStandaloneConfiguration redisConfiguration = new RedisStandaloneConfiguration(
                REDIS.getHost(),
                REDIS.getMappedPort(6379));
        LettuceConnectionFactory redisConnectionFactory = new LettuceConnectionFactory(redisConfiguration);
        redisConnectionFactory.afterPropertiesSet();
        redis = new RedisTemplate<>();
        redis.setConnectionFactory(redisConnectionFactory);
        redis.setKeySerializer(new KeyRedisSerializer(List.of(new RedisHashTag(TreeCode.ofSegment("WEB_REALTIME")))));
        redis.setValueSerializer(new StringRedisSerializer());
        redis.setHashKeySerializer(new StringRedisSerializer());
        redis.setHashValueSerializer(new StringRedisSerializer());
        redis.afterPropertiesSet();

        Config redissonConfig = new Config();
        redissonConfig
                .useSingleServer()
                .setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        redisson = Redisson.create(redissonConfig);
        rateLimitBuckets = Bucket4jRedisson.casBasedBuilder(((Redisson) redisson).getCommandExecutor())
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1)))
                .keyMapper(Mapper.STRING)
                .build();

        rabbitConnectionFactory = new CachingConnectionFactory(RABBITMQ.getHost(), RABBITMQ.getAmqpPort());
        rabbitConnectionFactory.setUsername("sca4dj");
        rabbitConnectionFactory.setPassword("sca4dj");
        rabbit = new RabbitTemplate(rabbitConnectionFactory);
        rabbit.setMandatory(true);
        rabbitAdmin = new RabbitAdmin(rabbitConnectionFactory);
    }

    /**
     * 关闭测试创建的 Redis 和 RabbitMQ 客户端。
     */
    @AfterAll
    static void disconnectInfrastructure() {
        redisson.shutdown();
        rabbitConnectionFactory.destroy();
    }

    @BeforeEach
    void createNodes() {
        Objects.requireNonNull(redis.getConnectionFactory()).getConnection().serverCommands().flushAll();
        clock = new MutableClock(Instant.parse("2026-07-28T00:00:00Z"));
        WebRealtimeInfrastructureNames names = new WebRealtimeInfrastructureNames();
        firstRegistry = new RedisConnectionRegistry(redis, names, PROPERTIES, clock, redisBatches());
        secondRegistry = new RedisConnectionRegistry(redis, names, PROPERTIES, clock, redisBatches());
        firstLocal = new LocalConnectionRegistry(firstRegistry);
        secondLocal = new LocalConnectionRegistry(secondRegistry);
        NodeMessageCodec codec = new NodeMessageCodec(OBJECT_MAPPER);
        firstNode = new RabbitNodeRouter(
                names,
                rabbit,
                rabbitConnectionFactory,
                codec,
                firstLocal,
                PROPERTIES);
        secondNode = new RabbitNodeRouter(
                names,
                rabbit,
                rabbitConnectionFactory,
                codec,
                secondLocal,
                PROPERTIES);
        declare(firstNode);
        declare(secondNode);
        firstNode.start();
        secondNode.start();
    }

    @AfterEach
    void stopNodes() {
        firstLocal.close();
        secondLocal.close();
        firstNode.stop();
        secondNode.stop();
    }

    @Test
    @DisplayName("Routes Account Topic And Subject Targets Across Two Nodes")
    void routesAccountTopicAndSubjectTargetsAcrossTwoNodes() throws InterruptedException {
        Session session = session();
        TestConnection firstConnection = new TestConnection("connection-1");
        TestConnection secondConnection = new TestConnection("connection-2");
        firstLocal.add(firstConnection);
        secondLocal.add(secondConnection);
        firstRegistry.register(firstConnection.id(), firstNode.nodeId(), session);
        secondRegistry.register(secondConnection.id(), secondNode.nodeId(), session);

        WebRealtimeMessageHandler handler = new WebRealtimeMessageHandler(
                firstRegistry,
                firstNode,
                PROPERTIES,
                clock,
                OBJECT_MAPPER,
                new WebRealtimeMessageCodec(OBJECT_MAPPER));
        MessageBus messages = (message, policy) -> {
            assertThat(policy.mode()).isEqualTo(DispatchPolicy.Mode.IMMEDIATE);
            handler.handle(
                    new IncomingMessage<>(
                            Id.of(1L),
                            (WebRealtimeMessage) message,
                            new Name("WEB_REALTIME_TEST"),
                            clock.instant(),
                            false,
                            1));
            return new DispatchReceipt(Id.of(1L), DispatchReceipt.Status.BROKER_ACCEPTED);
        };
        WebRealtimePublisher publisher = new DefaultWebRealtimePublisher(messages, PROPERTIES, clock);
        ObjectNode payload = OBJECT_MAPPER.createObjectNode().put("invoiceId", 42);
        Event<ObjectNode> event = new Event<>(
                new EventType<>("INVOICE/CHANGED", 1, ObjectNode.class),
                null,
                clock.instant(),
                payload);
        publisher.publish(
                WebRealtimeTarget.account(session.account().accountId()),
                event,
                DispatchPolicy.immediate());

        assertThat(firstConnection.received()).isNotNull();
        BrowserWrite crossNode = secondConnection.received();
        assertThat(crossNode).isNotNull();
        assertThat(crossNode.envelope().getEventCode()).isEqualTo("INVOICE/CHANGED");
        assertThat(crossNode.envelope().getProtocolVersion()).isEqualTo(1);
        assertThat(crossNode.envelope().getPayload().path("invoiceId").intValue()).isEqualTo(42);

        WebRealtimeTarget topic = WebRealtimeTarget.topic("invoice:42");
        assertThat(secondRegistry.subscribe(secondConnection.id(), "invoice", topic)).isTrue();
        publisher.publish(topic, event, DispatchPolicy.immediate());
        assertThat(secondConnection.received()).isNotNull();
        assertThat(firstConnection.received(300)).isNull();

        assertThat(
                firstRegistry.resolve(WebRealtimeTarget.subject(session.subject().subjectId()))
                        .values()
                        .stream()
                        .flatMap(List::stream)
                        .toList())
                .containsExactlyInAnyOrder(firstConnection.id(), secondConnection.id());
        secondRegistry.unregister(secondConnection.id());
        assertThat(
                firstRegistry.resolve(WebRealtimeTarget.account(session.account().accountId()))
                        .values()
                        .stream()
                        .flatMap(List::stream)
                        .toList())
                .containsExactly(firstConnection.id());

        clock.advance(Duration.ofSeconds(6));
        assertThat(firstRegistry.resolve(WebRealtimeTarget.account(session.account().accountId())))
                .isEmpty();
    }

    @Test
    @DisplayName("跨批次读取保留节点和 Session 归属，并清理失效连接索引")
    void resolvesConnectionsAcrossBatchBoundaries() {
        WebRealtimeProperties properties = new WebRealtimeProperties();
        properties.setLeaseDuration(Duration.ofMinutes(5));
        WebRealtimeInfrastructureNames names = new WebRealtimeInfrastructureNames();
        RedisConnectionRegistry registry = new RedisConnectionRegistry(redis, names, properties, clock, redisBatches());
        Session session = session();
        for (int index = 0; index < 1001; index++) {
            registry.register(new ConnectionId("batch-" + index), "node-" + (index % 2), session);
        }
        assertThat(registry.findBySession(session.id())).hasSize(1001);
        assertThat(registry.resolve(WebRealtimeTarget.account(session.account().accountId()))).hasSize(2);
        redis.delete(names.connection(new ConnectionId("batch-1000")));
        assertThat(registry.findBySession(session.id())).hasSize(1000);
        registry.terminateBySession(session.id(), "test cleanup");
        assertThat(registry.findBySession(session.id())).isEmpty();
    }

    /**
     * 创建使用默认命令上限的 Redis 批处理器。
     */
    private static RedisBatches redisBatches() {
        return new RedisBatches(new RedisPipelineProperties());
    }

    @Test
    @DisplayName("Consumes Ticket Once Before Upgrade And Resolves Current Session")
    void consumesTicketOnceBeforeUpgradeAndResolvesCurrentSession() {
        Session session = session();
        AtomicBoolean online = new AtomicBoolean(true);
        SessionLifecycle sessions = mock(SessionLifecycle.class);
        when(sessions.resolve(session.id()))
                .thenAnswer(ignored -> online.get() ? Optional.of(session) : Optional.empty());
        RedisTicketService tickets = new RedisTicketService(
                redis,
                sessions,
                new WebRealtimeInfrastructureNames(),
                PROPERTIES,
                clock);
        TicketHandshakeInterceptor handshake = new TicketHandshakeInterceptor(tickets, PROPERTIES);

        WebRealtimeTicket wrongPath = tickets.issue(session, "/events/ws");
        assertThat(
                handshake.beforeHandshake(
                        request("/other", "http://localhost:5173", wrongPath.value()),
                        response(),
                        new TextWebSocketHandler(),
                        new HashMap<>()))
                .isFalse();
        assertThat(
                handshake.beforeHandshake(
                        request("http://localhost:5173", wrongPath.value()),
                        response(),
                        new TextWebSocketHandler(),
                        new HashMap<>()))
                .isFalse();

        WebRealtimeTicket accepted = tickets.issue(session, "/events/ws");
        Map<String, Object> attributes = new HashMap<>();
        assertThat(
                handshake.beforeHandshake(
                        request("http://localhost:5173", accepted.value()),
                        response(),
                        new TextWebSocketHandler(),
                        attributes))
                .isTrue();
        assertThat(attributes.get(TicketHandshakeInterceptor.SESSION_ATTRIBUTE)).isSameAs(session);
        assertThat(
                handshake.beforeHandshake(
                        request("http://localhost:5173", accepted.value()),
                        response(),
                        new TextWebSocketHandler(),
                        new HashMap<>()))
                .isFalse();

        WebRealtimeTicket offline = tickets.issue(session, "/events/ws");
        online.set(false);
        assertThat(
                handshake.beforeHandshake(
                        request("http://localhost:5173", offline.value()),
                        response(),
                        new TextWebSocketHandler(),
                        new HashMap<>()))
                .isFalse();
    }

    @Test
    @DisplayName("Handshake Checks Origin Before Consuming The Single Ticket")
    void handshakeChecksOriginBeforeConsumingTheSingleTicket() {
        Session session = session();
        SessionLifecycle sessions = mock(SessionLifecycle.class);
        when(sessions.resolve(session.id())).thenReturn(Optional.of(session));
        RedisTicketService tickets = new RedisTicketService(
                redis,
                sessions,
                new WebRealtimeInfrastructureNames(),
                PROPERTIES,
                clock);
        TicketHandshakeInterceptor handshake = new TicketHandshakeInterceptor(tickets, PROPERTIES);
        WebRealtimeTicket ticket = tickets.issue(session, "/events/ws");

        assertThat(
                handshake.beforeHandshake(
                        request("http://untrusted.example", ticket.value()),
                        response(),
                        new TextWebSocketHandler(),
                        new HashMap<>()))
                .isFalse();

        Map<String, Object> attributes = new HashMap<>();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ServletServerHttpResponse acceptedResponse = new ServletServerHttpResponse(servletResponse);
        assertThat(
                handshake.beforeHandshake(
                        request("http://localhost:5173", ticket.value()),
                        acceptedResponse,
                        new TextWebSocketHandler(),
                        attributes))
                .isTrue();
        assertThat(attributes.get(TicketHandshakeInterceptor.SESSION_ATTRIBUTE)).isSameAs(session);
        assertThat(acceptedResponse.getHeaders().getCacheControl()).isEqualTo("no-store");

        assertThat(
                handshake.beforeHandshake(
                        request("http://localhost:5173", ticket.value()),
                        response(),
                        new TextWebSocketHandler(),
                        new HashMap<>()))
                .isFalse();
    }

    @Test
    @DisplayName("Enforces Shared Redis Token Bucket Burst Capacity And Continuous Refill")
    void enforcesSharedRedisTokenBucketBurstCapacityAndContinuousRefill() {
        StompInboundInterceptor inbound = new StompInboundInterceptor(
                new StompRateLimits(new RedisRateLimiter(rateLimitBuckets)),
                firstRegistry,
                List.of(),
                clock);
        Session session = session();
        MessageChannel channel = mock(MessageChannel.class);

        for (int attempt = 0; attempt < 20; attempt++) {
            assertThat(inbound.preSend(connect(session), channel)).isNotNull();
        }
        assertRateLimitRejectsWithin(
                () -> inbound.preSend(connect(session), channel),
                "STOMP CONNECT 超过限流",
                20);

        for (int attempt = 0; attempt < 120; attempt++) {
            assertThat(inbound.preSend(subscribe(session), channel)).isNotNull();
        }
        assertRateLimitRejectsWithin(
                () -> inbound.preSend(subscribe(session), channel),
                "STOMP SUBSCRIBE 超过限流",
                120);

        for (int attempt = 0; attempt < 60; attempt++) {
            assertThat(inbound.preSend(send(session), channel)).isNotNull();
        }
        assertRateLimitRejectsWithin(
                () -> inbound.preSend(send(session), channel),
                "STOMP SEND 超过限流",
                60);

    }

    /**
     * 在不假设固定窗口起止时刻的前提下确认持续请求会耗尽令牌。
     *
     * <p>Bucket4j 在请求期间连续补充令牌，因此容量后的第一次请求仍可能合法。
     */
    private static void assertRateLimitRejectsWithin(
            Runnable request,
            String expectedMessage,
            int maximumAttempts) {
        for (int attempt = 0; attempt < maximumAttempts; attempt++) {
            try {
                request.run();
            } catch (IllegalStateException exception) {
                assertThat(exception).hasMessage(expectedMessage);
                return;
            }
        }
        throw new AssertionError("持续请求未耗尽令牌桶: " + expectedMessage);
    }

    /**
     * 创建绑定握手 Session、远端 IP 和 STOMP 1.2 的 CONNECT 消息。
     */
    private static Message<?> connect(Session session) {
        StompHeaderAccessor headers = stompHeaders(session, StompCommand.CONNECT);
        headers.setAcceptVersion(WebRealtimeProtocol.STOMP_VERSION);
        Objects.requireNonNull(headers.getSessionAttributes())
                .put(TicketHandshakeInterceptor.IP_ATTRIBUTE, "192.0.2.20");
        return stompMessage(headers);
    }

    /**
     * 创建固定用户事件订阅消息，使测试只观察共享 SUBSCRIBE 限流。
     */
    private static Message<?> subscribe(Session session) {
        StompHeaderAccessor headers = businessHeaders(session, StompCommand.SUBSCRIBE, "connection-subscribe");
        headers.setDestination(WebRealtimeProtocol.USER_EVENT_DESTINATION);
        headers.setSubscriptionId("events");
        return stompMessage(headers);
    }

    /**
     * 创建 App destination 消息，使测试只观察共享 SEND 限流。
     */
    private static Message<?> send(Session session) {
        StompHeaderAccessor headers = businessHeaders(session, StompCommand.SEND, "connection-send");
        headers.setDestination(WebRealtimeProtocol.APPLICATION_DESTINATION_PREFIX + "/test");
        return stompMessage(headers);
    }

    /**
     * 创建已经登记 ConnectionId 且选择测试 Principal 的业务消息 header。
     */
    private static StompHeaderAccessor businessHeaders(
            Session session,
            StompCommand command,
            String connectionId) {
        StompHeaderAccessor headers = stompHeaders(session, command);
        Objects.requireNonNull(headers.getSessionAttributes())
                .put(StompConnections.class.getName() + ".connection", new ConnectionId(connectionId));
        headers.addNativeHeader(WebRealtimeProtocol.PRINCIPAL_ID_HEADER, "31");
        return headers;
    }

    /**
     * 创建具有独立 Spring Session 属性和票据握手 Session 的 STOMP header。
     */
    private static StompHeaderAccessor stompHeaders(Session session, StompCommand command) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(command);
        headers.setSessionId("stomp-integration-session");
        headers.setSessionAttributes(new HashMap<>());
        Objects.requireNonNull(headers.getSessionAttributes())
                .put(TicketHandshakeInterceptor.SESSION_ATTRIBUTE, session);
        headers.setLeaveMutable(true);
        return headers;
    }

    /**
     * 把 Spring STOMP header 包装为入站消息。
     */
    private static Message<?> stompMessage(StompHeaderAccessor headers) {
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    /**
     * 创建固定 `/events/ws` 路径的 WebSocket 握手请求。
     */
    private static ServletServerHttpRequest request(String origin, String ticket) {
        return request("/events/ws", origin, ticket);
    }

    /**
     * 创建指定握手路径、Origin 和 Ticket 的请求。
     */
    private static ServletServerHttpRequest request(String path, String origin, String ticket) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setQueryString("ticket=" + ticket);
        request.addHeader(HttpHeaders.ORIGIN, origin);
        request.setRemoteAddr("192.0.2.10");
        return new ServletServerHttpRequest(request);
    }

    /**
     * 创建用于读取握手响应头的空 Servlet 响应。
     */
    private static ServletServerHttpResponse response() {
        return new ServletServerHttpResponse(new MockHttpServletResponse());
    }

    /**
     * 使用 RabbitAdmin 声明指定节点的临时 exchange、queue 和 binding。
     */
    private static void declare(RabbitNodeRouter node) {
        for (Declarable declarable : node.declarables().getDeclarables()) {
            if (declarable instanceof Exchange exchange) {
                rabbitAdmin.declareExchange(exchange);
            } else if (declarable instanceof Queue queue) {
                rabbitAdmin.declareQueue(queue);
            } else if (declarable instanceof Binding binding) {
                rabbitAdmin.declareBinding(binding);
            }
        }
    }

    /**
     * 创建包含一个可选 Principal 的有效测试 Session。
     */
    private static Session session() {
        Instant issuedAt = Instant.parse("2026-07-28T00:00:00Z");
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
                                TreeCode.ofSlash("AUTHENTICATION/TEST"),
                                Set.of(AuthenticationFactorKind.KNOWLEDGE),
                                issuedAt)),
                Map.of(principal.principalId(), List.of()),
                Map.of(),
                issuedAt,
                issuedAt.plus(Duration.ofHours(1)));
    }

    /**
     * 保存节点投递，供测试线程等待并检查。
     */
    private static final class TestConnection implements LocalConnection {
        private final ConnectionId id;

        private final BlockingQueue<BrowserWrite> deliveries = new LinkedBlockingQueue<>();

        private TestConnection(String id) {
            this.id = new ConnectionId(id);
        }

        @Override
        public @NotNull ConnectionId id() {
            return id;
        }

        @Override
        public void offer(@NotNull BrowserWrite delivery) {
            deliveries.add(delivery);
        }

        @Override
        public void close() {
        }

        /**
         * 最多等待五秒并返回节点投递结果。
         */
        private BrowserWrite received() throws InterruptedException {
            return received(5_000);
        }

        /**
         * 在指定毫秒数内等待节点投递，超时返回 null。
         */
        private BrowserWrite received(long timeoutMillis) throws InterruptedException {
            return deliveries.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 允许测试推进 Redis 索引使用的逻辑时间。
     */
    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("测试时钟只使用 UTC");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        /**
         * 把逻辑时间向前推进指定时长。
         */
        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
