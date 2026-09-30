package com.sca4d.web.realtime.stomp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.web.realtime.config.WebRealtimeProperties;
import com.sca4d.web.realtime.ticket.TicketHandshakeInterceptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.StompBrokerRelayRegistration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.util.List;
import java.util.Objects;

/** 使用 Spring STOMP 配置 WebSocket 端点、broker 模式和入出站拦截器。 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
@ApiStatus.Internal
public class StompConfiguration implements WebSocketMessageBrokerConfigurer {

    private final @NotNull ObjectMapper objectMapper;

    private final @NotNull WebRealtimeProperties properties;

    private final @NotNull StompChannelDispatcher inboundDispatcher;

    private final @NotNull StompChannelDispatcher outboundDispatcher;

    private final @NotNull StompInboundInterceptor inboundInterceptor;

    private final @NotNull StompOutboundInterceptor outboundInterceptor;

    private final @NotNull TicketHandshakeInterceptor handshakeInterceptor;

    private final @NotNull WebSocketSessions sockets;

    /** 创建 Spring STOMP 基础设施配置。 */
    public StompConfiguration(
            @NotNull WebRealtimeProperties properties,
            @NotNull TicketHandshakeInterceptor handshakeInterceptor,
            @NotNull WebSocketSessions sockets,
            @NotNull StompInboundInterceptor inboundInterceptor,
            @NotNull StompOutboundInterceptor outboundInterceptor,
            @NotNull ObjectMapper objectMapper,
            @Qualifier("stompInboundDispatcher") @NotNull StompChannelDispatcher inboundDispatcher,
            @Qualifier("stompOutboundDispatcher") @NotNull StompChannelDispatcher outboundDispatcher) {
        this.properties = Objects.requireNonNull(properties, "Web Realtime 配置不能为空");
        this.handshakeInterceptor = Objects.requireNonNull(handshakeInterceptor, "WebSocket 握手拦截器不能为空");
        this.sockets = Objects.requireNonNull(sockets, "WebSocket 连接句柄不能为空");
        this.inboundInterceptor = Objects.requireNonNull(inboundInterceptor, "STOMP 入站拦截器不能为空");
        this.outboundInterceptor = Objects.requireNonNull(outboundInterceptor, "STOMP 出站拦截器不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
        this.inboundDispatcher = Objects.requireNonNull(inboundDispatcher, "STOMP 入站分派器不能为空");
        this.outboundDispatcher = Objects.requireNonNull(outboundDispatcher, "STOMP 出站分派器不能为空");
    }

    /** 注册固定 `/events/ws` 原生 WebSocket STOMP 入口，不启用 SockJS。 */
    @Override
    public void registerStompEndpoints(@NotNull StompEndpointRegistry registry) {
        // 同连接顺序由有界分派器维护，不再经过 Spring 的无界顺序队列。
        registry.setPreserveReceiveOrder(false);
        registry.addEndpoint(WebRealtimeProtocol.WEBSOCKET_ENDPOINT)
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins(properties.getAllowedOrigins().toArray(String[]::new));
    }

    /** 把帧大小、首帧时间、写出时间和缓冲限制交给 Spring WebSocket transport。 */
    @Override
    public void configureWebSocketTransport(@NotNull WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(properties.getInboundFrameMaxBytes())
                .setTimeToFirstMessage((int) properties.getStompConnectTimeout().toMillis())
                .setSendTimeLimit((int) properties.getStomp().getSendTimeLimit().toMillis())
                .setSendBufferSizeLimit(properties.getStomp().getSendBufferMaxBytes())
                .addDecoratorFactory(sockets);
    }

    /** 为 CONNECT、SUBSCRIBE、UNSUBSCRIBE 和 SEND 加入身份、限流和授权检查。 */
    @Override
    public void configureClientInboundChannel(@NotNull ChannelRegistration registration) {
        registration.executor(inboundDispatcher).interceptors(inboundDispatcher, inboundInterceptor);
    }

    /** 在 Spring 确认浏览器事件已经写出后关闭失效的连接。 */
    @Override
    public void configureClientOutboundChannel(@NotNull ChannelRegistration registration) {
        registration.executor(outboundDispatcher).interceptors(outboundDispatcher, outboundInterceptor);
    }

    /** 配置本地 simple broker 或 RabbitMQ STOMP relay，并固定应用和用户 destination 前缀。 */
    @Override
    public void configureMessageBroker(@NotNull MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes(WebRealtimeProtocol.APPLICATION_DESTINATION_PREFIX)
                .setUserDestinationPrefix(WebRealtimeProtocol.USER_DESTINATION_PREFIX)
                .setPreservePublishOrder(false);
        if (properties.getStomp().getMode() == StompBrokerMode.NODE_ROUTED) {
            registry.enableSimpleBroker(
                    WebRealtimeProtocol.QUEUE_DESTINATION_PREFIX,
                    WebRealtimeProtocol.TOPIC_DESTINATION_PREFIX);
            return;
        }
        WebRealtimeProperties.Relay relay = properties.getStomp().getRelay();
        StompBrokerRelayRegistration registration = registry.enableStompBrokerRelay(
                WebRealtimeProtocol.QUEUE_DESTINATION_PREFIX,
                WebRealtimeProtocol.TOPIC_DESTINATION_PREFIX);
        registration.setRelayHost(relay.getHost())
                .setRelayPort(relay.getPort())
                .setClientLogin(relay.getClientLogin())
                .setClientPasscode(relay.getClientPasscode())
                .setSystemLogin(relay.getSystemLogin())
                .setSystemPasscode(relay.getSystemPasscode())
                .setVirtualHost(relay.getVirtualHost());
    }

    /** 让 BrowserEnvelope 与 App 的 @MessageMapping 使用脚手架固定 JSON 映射器。 */
    @Override
    public boolean configureMessageConverters(@NotNull List<MessageConverter> converters) {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        converters.add(converter);
        return true;
    }
}
