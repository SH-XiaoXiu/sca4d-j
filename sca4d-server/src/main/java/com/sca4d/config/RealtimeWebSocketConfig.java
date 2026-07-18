package com.sca4d.config;

import com.sca4d.common.auth.messaging.MessagingAccountArgumentResolver;
import com.sca4d.common.auth.messaging.MessagingPrincipalArgumentResolver;
import com.sca4d.common.auth.messaging.MessagingSubjectArgumentResolver;
import com.sca4d.common.realtime.ws.RealtimeHandshakeHandler;
import com.sca4d.common.realtime.ws.RealtimeHandshakeInterceptor;
import com.sca4d.common.realtime.ws.StompFrameRateLimitInterceptor;
import com.sca4d.common.realtime.ws.StompRequestInterceptor;
import com.sca4d.common.realtime.ws.StompSubscriptionAuthorizationInterceptor;
import com.sca4d.config.properties.Sca4djRealtimeProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.StompBrokerRelayRegistration;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * WebSocket 与 STOMP 传输配置。
 */
@Configuration
@EnableWebSocketMessageBroker
@ConditionalOnProperty(prefix = "sca4dj.realtime", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RealtimeWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final Sca4djRealtimeProperties properties;
    private final RealtimeHandshakeInterceptor handshakeInterceptor;
    private final RealtimeHandshakeHandler handshakeHandler;
    private final StompRequestInterceptor requestInterceptor;
    private final List<StompFrameRateLimitInterceptor> frameRateLimitInterceptors;
    private final List<StompSubscriptionAuthorizationInterceptor> subscriptionAuthorizationInterceptors;
    private final MessagingSubjectArgumentResolver subjectArgumentResolver;
    private final MessagingAccountArgumentResolver accountArgumentResolver;
    private final MessagingPrincipalArgumentResolver principalArgumentResolver;

    /**
     * 创建全局装配握手、请求态、限流、订阅授权和参数注入的 STOMP 配置。
     *
     * @param properties 实时传输、端点和 broker 配置
     * @param handshakeInterceptor 握手认证请求态拦截器
     * @param handshakeHandler WebSocket Subject Principal 创建器
     * @param requestInterceptor 每帧请求态恢复与清理拦截器
     * @param frameRateLimitInterceptors 当前应用提供的帧级限流拦截器
     * @param subscriptionAuthorizationInterceptors 当前应用提供的订阅授权拦截器
     * @param subjectArgumentResolver STOMP Subject 参数解析器
     * @param accountArgumentResolver STOMP Account 参数解析器
     * @param principalArgumentResolver STOMP Principal 参数解析器
     */
    public RealtimeWebSocketConfig(
            @NotNull Sca4djRealtimeProperties properties,
            @NotNull RealtimeHandshakeInterceptor handshakeInterceptor,
            @NotNull RealtimeHandshakeHandler handshakeHandler,
            @NotNull StompRequestInterceptor requestInterceptor,
            @NotNull List<StompFrameRateLimitInterceptor> frameRateLimitInterceptors,
            @NotNull List<StompSubscriptionAuthorizationInterceptor> subscriptionAuthorizationInterceptors,
            @NotNull MessagingSubjectArgumentResolver subjectArgumentResolver,
            @NotNull MessagingAccountArgumentResolver accountArgumentResolver,
            @NotNull MessagingPrincipalArgumentResolver principalArgumentResolver) {
        this.properties = properties;
        this.handshakeInterceptor = handshakeInterceptor;
        this.handshakeHandler = handshakeHandler;
        this.requestInterceptor = requestInterceptor;
        this.frameRateLimitInterceptors = List.copyOf(frameRateLimitInterceptors);
        this.subscriptionAuthorizationInterceptors = List.copyOf(subscriptionAuthorizationInterceptors);
        this.subjectArgumentResolver = subjectArgumentResolver;
        this.accountArgumentResolver = accountArgumentResolver;
        this.principalArgumentResolver = principalArgumentResolver;
    }

    /**
     * 按配置注册唯一 STOMP 握手端点，并可选启用 SockJS 兼容传输。
     *
     * @param registry Spring WebSocket 端点注册表
     */
    @Override
    public void registerStompEndpoints(@NotNull StompEndpointRegistry registry) {
        if (!properties.getWebsocket().isEnabled()) {
            return;
        }
        StompWebSocketEndpointRegistration registration = registry.addEndpoint(properties.getWebsocket().getEndpoint())
                .setHandshakeHandler(handshakeHandler)
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns(properties.getWebsocket()
                        .getAllowedOriginPatterns()
                        .toArray(String[]::new));
        if (properties.getWebsocket().isSockJsEnabled()) {
            registration.withSockJS();
        }
    }

    /**
     * 配置应用目标、用户目标和消息 broker；relay 启用时不再创建进程内 simple broker。
     *
     * @param registry Spring STOMP broker 注册表
     */
    @Override
    public void configureMessageBroker(@NotNull MessageBrokerRegistry registry) {
        if (!properties.getWebsocket().isEnabled()) {
            return;
        }
        registry.setApplicationDestinationPrefixes(properties.getWebsocket()
                .getApplicationDestinationPrefixes()
                .toArray(String[]::new));
        registry.setUserDestinationPrefix(properties.getWebsocket().getUserDestinationPrefix());
        if (properties.getWebsocket().getBrokerRelay().isEnabled()) {
            configureBrokerRelay(registry);
            return;
        }
        registry.enableSimpleBroker(properties.getWebsocket().getBrokerDestinationPrefixes().toArray(String[]::new));
    }

    /**
     * 按请求态、限流、订阅授权的固定顺序注册入站拦截器。
     *
     * <p>每个配置列表只复制和追加一次，不执行数据库或外部调用。
     *
     * @param registration 客户端入站 Channel 注册表
     */
    @Override
    public void configureClientInboundChannel(@NotNull ChannelRegistration registration) {
        List<ChannelInterceptor> interceptors = new ArrayList<>();
        interceptors.add(requestInterceptor);
        interceptors.addAll(frameRateLimitInterceptors);
        interceptors.addAll(subscriptionAuthorizationInterceptors);
        registration.interceptors(interceptors.toArray(ChannelInterceptor[]::new));
    }

    /**
     * 注册 Subject、Account 和 Principal 三类 STOMP 方法参数解析器。
     *
     * @param argumentResolvers Spring Messaging 方法参数解析器列表
     */
    @Override
    public void addArgumentResolvers(@NotNull List<HandlerMethodArgumentResolver> argumentResolvers) {
        argumentResolvers.add(subjectArgumentResolver);
        argumentResolvers.add(accountArgumentResolver);
        argumentResolvers.add(principalArgumentResolver);
    }

    /**
     * 使用当前实时配置连接外部 STOMP broker relay。
     *
     * <p>用户目标广播和用户注册广播只有在配置非空时才启用，避免单节点部署产生无效内部 topic。
     *
     * @param registry 尚未选择 broker 实现的 STOMP 注册表
     */
    private void configureBrokerRelay(@NotNull MessageBrokerRegistry registry) {
        Sca4djRealtimeProperties.BrokerRelay relay = properties.getWebsocket().getBrokerRelay();
        StompBrokerRelayRegistration registration = registry.enableStompBrokerRelay(properties.getWebsocket()
                .getBrokerDestinationPrefixes()
                .toArray(String[]::new));
        registration.setRelayHost(relay.getHost())
                .setRelayPort(relay.getPort())
                .setClientLogin(relay.getClientLogin())
                .setClientPasscode(relay.getClientPasscode())
                .setSystemLogin(relay.getSystemLogin())
                .setSystemPasscode(relay.getSystemPasscode());
        if (StringUtils.hasText(properties.getWebsocket().getUserDestinationBroadcast())) {
            registration.setUserDestinationBroadcast(properties.getWebsocket().getUserDestinationBroadcast());
        }
        if (StringUtils.hasText(properties.getWebsocket().getUserRegistryBroadcast())) {
            registration.setUserRegistryBroadcast(properties.getWebsocket().getUserRegistryBroadcast());
        }
    }
}
