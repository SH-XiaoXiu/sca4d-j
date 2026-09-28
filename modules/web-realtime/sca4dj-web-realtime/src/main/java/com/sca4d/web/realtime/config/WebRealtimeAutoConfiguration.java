package com.sca4d.web.realtime.config;

import com.sca4d.core.api.authentication.SessionLifecycle;
import com.sca4d.core.api.concurrent.RateLimiter;
import com.sca4d.core.api.messaging.MessageBus;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.json.Sca4djMapper;
import com.sca4d.core.spring.redis.RedisBatches;
import com.sca4d.core.spring.redis.RedisHashTag;
import com.sca4d.core.spring.redis.Sca4djRedis;
import com.sca4d.web.realtime.connection.DefaultWebRealtimePublisher;
import com.sca4d.web.realtime.connection.LocalConnectionRegistry;
import com.sca4d.web.realtime.connection.LocalNodeRouter;
import com.sca4d.web.realtime.connection.NodeRouter;
import com.sca4d.web.realtime.connection.RedisConnectionRegistry;
import com.sca4d.web.realtime.connection.WebRealtimeMessageCodec;
import com.sca4d.web.realtime.connection.WebRealtimeMessageHandler;
import com.sca4d.web.realtime.rabbit.NodeMessageCodec;
import com.sca4d.web.realtime.rabbit.RabbitNodeRouter;
import com.sca4d.web.realtime.sse.SseConnections;
import com.sca4d.web.realtime.sse.WebRealtimeSseConnector;
import com.sca4d.web.realtime.stomp.StompChannelDispatcher;
import com.sca4d.web.realtime.stomp.StompConfiguration;
import com.sca4d.web.realtime.stomp.StompConnections;
import com.sca4d.web.realtime.stomp.StompInboundInterceptor;
import com.sca4d.web.realtime.stomp.StompOutboundInterceptor;
import com.sca4d.web.realtime.stomp.StompRateLimits;
import com.sca4d.web.realtime.stomp.StompRelayPortCheck;
import com.sca4d.web.realtime.stomp.WebRealtimeSubscriptionPolicy;
import com.sca4d.web.realtime.stomp.WebSocketSessions;
import com.sca4d.web.realtime.ticket.RedisTicketService;
import com.sca4d.web.realtime.ticket.TicketHandshakeInterceptor;
import org.jetbrains.annotations.NotNull;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.util.List;

/** 自动装配共享连接索引、可选节点传输、SSE、WebSocket/STOMP 和一次性票据。 */
@AutoConfiguration(after = RabbitAutoConfiguration.class)
@EnableConfigurationProperties(WebRealtimeProperties.class)
@EnableScheduling
@Import(StompConfiguration.class)
public class WebRealtimeAutoConfiguration {

    /** 连接和目标索引由 Lua 同时修改，必须位于同一 Redis Cluster 槽。 */
    @Bean
    RedisHashTag webRealtimeRedisHashTag() {
        return new RedisHashTag(TreeCode.ofSegment("WEB_REALTIME"));
    }

    /** @return Web Realtime 全部 Redis 键和 RabbitMQ 对象的统一运行时名称 */
    @Bean
    WebRealtimeInfrastructureNames webRealtimeInfrastructureNames() {
        return new WebRealtimeInfrastructureNames();
    }

    /** @return Redis 共享连接、目标和租约索引 */
    @Bean
    RedisConnectionRegistry redisConnectionRegistry(
            @Sca4djRedis @NotNull RedisTemplate<Key, String> redis,
            @NotNull WebRealtimeInfrastructureNames names,
            @NotNull WebRealtimeProperties properties,
            @NotNull Clock clock,
            @NotNull RedisBatches redisBatches) {
        return new RedisConnectionRegistry(redis, names, properties, clock, redisBatches);
    }

    /** @return 当前 JVM 的不可迁移连接句柄注册表 */
    @Bean(destroyMethod = "close")
    LocalConnectionRegistry localWebRealtimeConnections(
            @NotNull RedisConnectionRegistry shared) {
        return new LocalConnectionRegistry(shared);
    }

    /** @return RabbitMQ 节点消息编解码器 */
    @Bean
    @ConditionalOnProperty(prefix = "sca4dj.web-realtime.node", name = "transport", havingValue = "RABBIT")
    NodeMessageCodec nodeMessageCodec(@Sca4djJson @NotNull Sca4djMapper objectMapper) {
        return new NodeMessageCodec(objectMapper);
    }

    /** @return 当前节点的独占自动删除队列和消息转发器 */
    @Bean
    @ConditionalOnProperty(prefix = "sca4dj.web-realtime.node", name = "transport", havingValue = "RABBIT")
    RabbitNodeRouter rabbitNodeRouter(
            @NotNull WebRealtimeInfrastructureNames names,
            @NotNull RabbitTemplate rabbitTemplate,
            @NotNull ConnectionFactory connectionFactory,
            @NotNull NodeMessageCodec codec,
            @NotNull LocalConnectionRegistry localConnections,
            @NotNull WebRealtimeProperties properties) {
        return new RabbitNodeRouter(
                names,
                rabbitTemplate,
                connectionFactory,
                codec,
                localConnections,
                properties);
    }

    /** @return 当前节点临时 exchange、queue 和 binding 声明 */
    @Bean
    @ConditionalOnProperty(prefix = "sca4dj.web-realtime.node", name = "transport", havingValue = "RABBIT")
    Declarables webRealtimeDeclarables(@NotNull RabbitNodeRouter node) {
        return node.declarables();
    }

    /** @return 不依赖 RabbitMQ 的当前 JVM 节点传输 */
    @Bean
    @ConditionalOnProperty(prefix = "sca4dj.web-realtime.node", name = "transport", havingValue = "LOCAL", matchIfMissing = true)
    LocalNodeRouter localNodeRouter(@NotNull LocalConnectionRegistry connections) {
        return new LocalNodeRouter(connections);
    }

    /** @return Web Realtime Message 与 RabbitMQ payload 之间的稳定转换 */
    @Bean
    WebRealtimeMessageCodec webRealtimeMessageCodec(@Sca4djJson @NotNull Sca4djMapper objectMapper) {
        return new WebRealtimeMessageCodec(objectMapper);
    }

    /** @return 把应用实时事件转换为 MessageBus 消息的公共入口 */
    @Bean
    DefaultWebRealtimePublisher webRealtimePublisher(
            @NotNull MessageBus messages,
            @NotNull WebRealtimeProperties properties,
            @NotNull Clock clock) {
        return new DefaultWebRealtimePublisher(messages, properties, clock);
    }

    /** @return Redis 目标解析和节点定向写出的 MessageHandler */
    @Bean
    WebRealtimeMessageHandler webRealtimeMessageHandler(
            @NotNull RedisConnectionRegistry registry,
            @NotNull NodeRouter nodeRouter,
            @NotNull WebRealtimeProperties properties,
            @NotNull Clock clock,
            @Sca4djJson @NotNull Sca4djMapper objectMapper,
            @NotNull WebRealtimeMessageCodec codec) {
        return new WebRealtimeMessageHandler(registry, nodeRouter, properties, clock, objectMapper, codec);
    }

    /** @return 使用有界串行写出和统一浏览器信封的 SSE 连接入口 */
    @Bean(destroyMethod = "close")
    WebRealtimeSseConnector webRealtimeSseConnector(
            @NotNull RedisConnectionRegistry shared,
            @NotNull LocalConnectionRegistry local,
            @NotNull NodeRouter nodeTransport,
            @NotNull WebRealtimeProperties properties) {
        return new SseConnections(shared, local, nodeTransport, properties);
    }

    /** @return Redis 一次性 WebSocket 票据服务 */
    @Bean
    RedisTicketService redisTicketService(
            @Sca4djRedis @NotNull RedisTemplate<Key, String> redis,
            @NotNull SessionLifecycle sessions,
            @NotNull WebRealtimeInfrastructureNames names,
            @NotNull WebRealtimeProperties properties,
            @NotNull Clock clock) {
        return new RedisTicketService(redis, sessions, names, properties, clock);
    }

    /** @return Web Realtime 选择 STOMP 限额和维度的协议策略 */
    @Bean
    StompRateLimits stompRateLimits(@NotNull RateLimiter limiter) {
        return new StompRateLimits(limiter);
    }

    /** @return 在 Spring WebSocket transport 中保存当前实际连接句柄的装饰器 */
    @Bean
    WebSocketSessions webSocketSessions() {
        return new WebSocketSessions();
    }

    /** @return 独立预算的 STOMP 入站有界分派器，不参与应用默认 Executor 选择 */
    @Bean(destroyMethod = "close", defaultCandidate = false)
    StompChannelDispatcher stompInboundDispatcher(WebRealtimeProperties properties, WebSocketSessions sockets) {
        return new StompChannelDispatcher("inbound", properties.getStomp(), sockets);
    }

    /** @return 独立预算的 STOMP 出站有界分派器，不参与应用默认 Executor 选择 */
    @Bean(destroyMethod = "close", defaultCandidate = false)
    StompChannelDispatcher stompOutboundDispatcher(WebRealtimeProperties properties, WebSocketSessions sockets) {
        return new StompChannelDispatcher("outbound", properties.getStomp(), sockets);
    }

    /** @return Spring STOMP Session 到 Redis 路由和本机连接句柄的生命周期桥接 */
    @Bean
    StompConnections stompConnections(
            @NotNull RedisConnectionRegistry shared,
            @NotNull LocalConnectionRegistry local,
            @NotNull NodeRouter nodeTransport,
            @NotNull SimpMessagingTemplate messagingTemplate,
            @NotNull WebSocketSessions sockets) {
        return new StompConnections(shared, local, nodeTransport, messagingTemplate, sockets);
    }

    /** @return Spring STOMP 入站命令认证、限流、订阅和 Call 拦截器 */
    @Bean
    StompInboundInterceptor stompInboundInterceptor(
            @NotNull StompRateLimits rateLimits,
            @NotNull RedisConnectionRegistry shared,
            @NotNull List<@NotNull WebRealtimeSubscriptionPolicy> subscriptionPolicies,
            @NotNull Clock clock) {
        return new StompInboundInterceptor(rateLimits, shared, subscriptionPolicies, clock);
    }

    /** @return Spring 写出失效事件后的 WebSocket 定向关闭拦截器 */
    @Bean
    StompOutboundInterceptor stompOutboundInterceptor(
            @NotNull WebSocketSessions sockets) {
        return new StompOutboundInterceptor(sockets);
    }

    /** @return 在 Upgrade 前校验 Origin 并消费一次性票据的拦截器 */
    @Bean
    TicketHandshakeInterceptor ticketHandshakeInterceptor(
            @NotNull RedisTicketService tickets,
            @NotNull WebRealtimeProperties properties) {
        return new TicketHandshakeInterceptor(tickets, properties);
    }

    /** @return `BROKER_RELAY` 模式启动时的 RabbitMQ STOMP plugin 可连接性检查 */
    @Bean
    StompRelayPortCheck stompRelayPortCheck(
            @NotNull WebRealtimeProperties properties) {
        return new StompRelayPortCheck(properties);
    }
}
