package com.sca4d.config;

import com.sca4d.common.auth.Authentication;
import com.sca4d.common.auth.messaging.MessagingAccountArgumentResolver;
import com.sca4d.common.auth.messaging.MessagingPrincipalArgumentResolver;
import com.sca4d.common.auth.messaging.MessagingSubjectArgumentResolver;
import com.sca4d.common.concurrent.ratelimit.RateLimitKeyGenerator;
import com.sca4d.common.concurrent.ratelimit.RateLimiter;
import com.sca4d.common.messaging.MessagePublisher;
import com.sca4d.common.realtime.DefaultRealtimeConnectionTargetResolver;
import com.sca4d.common.realtime.DefaultRealtimePublisher;
import com.sca4d.common.realtime.RealtimeConnectionTargetResolver;
import com.sca4d.common.realtime.RealtimePublisher;
import com.sca4d.common.realtime.sse.RabbitSseMessagePublisher;
import com.sca4d.common.realtime.sse.SseConnectionRegistry;
import com.sca4d.common.realtime.sse.SseConnectionService;
import com.sca4d.common.realtime.sse.SseMessagePublisher;
import com.sca4d.common.realtime.sse.SseRealtimeMessageListener;
import com.sca4d.common.realtime.ws.RealtimeHandshakeHandler;
import com.sca4d.common.realtime.ws.RealtimeHandshakeInterceptor;
import com.sca4d.common.realtime.ws.StompFrameRateLimitInterceptor;
import com.sca4d.common.realtime.ws.StompRequestInterceptor;
import com.sca4d.common.realtime.ws.StompSubscriptionAuthorizationInterceptor;
import com.sca4d.config.properties.Sca4djConcurrentProperties;
import com.sca4d.config.properties.Sca4djRealtimeProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * 实时通信配置。
 */
@Configuration
@EnableConfigurationProperties({Sca4djRealtimeProperties.class, Sca4djConcurrentProperties.class})
@ConditionalOnProperty(prefix = "sca4dj.realtime", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RealtimeConfig {

    /**
     * 注册实时握手拦截器。
     *
     * @param authentication 读取认证会话的实现
     * @return 实时握手拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull RealtimeHandshakeInterceptor realtimeHandshakeInterceptor(
            @NotNull Authentication authentication) {
        return new RealtimeHandshakeInterceptor(authentication);
    }

    /**
     * 注册实时握手处理器。
     *
     * @return 实时握手处理器
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull RealtimeHandshakeHandler realtimeHandshakeHandler() {
        return new RealtimeHandshakeHandler();
    }

    /**
     * 注册 STOMP 请求上下文拦截器。
     *
     * @return STOMP 请求上下文拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull StompRequestInterceptor stompRequestInterceptor() {
        return new StompRequestInterceptor();
    }

    /**
     * 注册 Messaging Subject 参数解析器。
     *
     * @return Messaging Subject 参数解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull MessagingSubjectArgumentResolver messagingSubjectArgumentResolver() {
        return new MessagingSubjectArgumentResolver();
    }

    /**
     * 注册 Messaging Account 参数解析器。
     *
     * @return Messaging Account 参数解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull MessagingAccountArgumentResolver messagingAccountArgumentResolver() {
        return new MessagingAccountArgumentResolver();
    }

    /**
     * 注册 Messaging Principal 参数解析器。
     *
     * @return Messaging Principal 参数解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull MessagingPrincipalArgumentResolver messagingPrincipalArgumentResolver() {
        return new MessagingPrincipalArgumentResolver();
    }

    /**
     * 注册 STOMP 帧级限流拦截器。
     *
     * @param rateLimiter 限流器
     * @param keyGenerator 限流 key 生成器
     * @param concurrentProperties 并发控制配置
     * @param realtimeProperties 实时通信配置
     * @param messagingTemplate STOMP 消息模板
     * @return STOMP 帧级限流拦截器
     */
    @Bean
    @ConditionalOnBean(RateLimiter.class)
    @ConditionalOnMissingBean
    public @NotNull StompFrameRateLimitInterceptor stompFrameRateLimitInterceptor(
            @NotNull RateLimiter rateLimiter,
            @NotNull RateLimitKeyGenerator keyGenerator,
            @NotNull Sca4djConcurrentProperties concurrentProperties,
            @NotNull Sca4djRealtimeProperties realtimeProperties,
            @NotNull SimpMessagingTemplate messagingTemplate) {
        return new StompFrameRateLimitInterceptor(
                rateLimiter, keyGenerator, concurrentProperties, realtimeProperties, messagingTemplate);
    }

    /**
     * 注册 STOMP 低层实时订阅授权拦截器。
     *
     * @param connectionTargetResolver 实时连接目标解析器
     * @param messagingTemplate STOMP 消息模板
     * @return STOMP 订阅授权拦截器
     */
    @Bean
    @ConditionalOnBean({RealtimeConnectionTargetResolver.class, SimpMessagingTemplate.class})
    @ConditionalOnMissingBean
    public @NotNull StompSubscriptionAuthorizationInterceptor stompSubscriptionAuthorizationInterceptor(
            @NotNull RealtimeConnectionTargetResolver connectionTargetResolver,
            @NotNull SimpMessagingTemplate messagingTemplate) {
        return new StompSubscriptionAuthorizationInterceptor(connectionTargetResolver, messagingTemplate);
    }

    /**
     * 注册默认实时连接目标解析器。
     *
     * @return 实时连接目标解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull RealtimeConnectionTargetResolver realtimeConnectionTargetResolver() {
        return new DefaultRealtimeConnectionTargetResolver();
    }

    /**
     * 注册默认实时消息发布器。
     *
     * @param messagingTemplate STOMP 消息模板
     * @param sseConnectionRegistry SSE 本机连接注册表提供器
     * @param sseMessagePublisher SSE 跨实例消息发布器提供器
     * @param realtimeProperties 实时通信配置
     * @return 实时消息发布器
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull RealtimePublisher realtimePublisher(
            @NotNull ObjectProvider<SimpMessagingTemplate> messagingTemplate,
            @NotNull ObjectProvider<SseConnectionRegistry> sseConnectionRegistry,
            @NotNull ObjectProvider<SseMessagePublisher> sseMessagePublisher,
            @NotNull Sca4djRealtimeProperties realtimeProperties) {
        return new DefaultRealtimePublisher(
                messagingTemplate.getIfAvailable(),
                sseConnectionRegistry.getIfAvailable(),
                sseMessagePublisher.getIfAvailable(),
                realtimeProperties);
    }

    /**
     * 注册 RabbitMQ SSE 跨实例消息发布器。
     *
     * @param messagePublisher 共用消息发布器
     * @param realtimeProperties 实时通信配置
     * @return SSE 跨实例消息发布器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "sca4dj.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = "sca4dj.realtime.sse", name = "enabled", havingValue = "true", matchIfMissing = true)
    public @NotNull SseMessagePublisher sseMessagePublisher(
            @NotNull MessagePublisher messagePublisher, @NotNull Sca4djRealtimeProperties realtimeProperties) {
        return new RabbitSseMessagePublisher(messagePublisher, realtimeProperties);
    }

    /**
     * 注册 SSE 本机连接注册表。
     *
     * @return SSE 本机连接注册表
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull SseConnectionRegistry sseConnectionRegistry() {
        return new SseConnectionRegistry();
    }

    /**
     * 注册 SSE 连接服务。
     *
     * @param registry SSE 本机连接注册表
     * @param connectionTargetResolver 实时连接目标解析器
     * @param realtimeProperties 实时通信配置
     * @return SSE 连接服务
     */
    @Bean
    @ConditionalOnBean(SseConnectionRegistry.class)
    @ConditionalOnMissingBean
    public @NotNull SseConnectionService sseConnectionService(
            @NotNull SseConnectionRegistry registry,
            @NotNull RealtimeConnectionTargetResolver connectionTargetResolver,
            @NotNull Sca4djRealtimeProperties realtimeProperties) {
        return new SseConnectionService(registry, connectionTargetResolver, realtimeProperties);
    }

    /**
     * 注册 SSE RabbitMQ 消息监听器。
     *
     * @param registry SSE 本机连接注册表
     * @return SSE RabbitMQ 消息监听器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "sca4dj.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = "sca4dj.realtime.sse", name = "enabled", havingValue = "true", matchIfMissing = true)
    public @NotNull SseRealtimeMessageListener sseRealtimeMessageListener(
            @NotNull SseConnectionRegistry registry) {
        return new SseRealtimeMessageListener(registry);
    }

}
