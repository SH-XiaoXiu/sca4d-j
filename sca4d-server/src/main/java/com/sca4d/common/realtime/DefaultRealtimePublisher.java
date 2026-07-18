package com.sca4d.common.realtime;

import com.sca4d.common.realtime.sse.SseConnectionRegistry;
import com.sca4d.common.realtime.sse.SseMessagePublisher;
import com.sca4d.config.properties.Sca4djRealtimeProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * 默认实时消息发布器。
 */
public class DefaultRealtimePublisher implements RealtimePublisher {

    private static final Logger log = LoggerFactory.getLogger(DefaultRealtimePublisher.class);

    private final @Nullable SimpMessagingTemplate messagingTemplate;
    private final @Nullable SseConnectionRegistry sseConnectionRegistry;
    private final @Nullable SseMessagePublisher sseMessagePublisher;
    private final Sca4djRealtimeProperties properties;

    /**
     * 创建默认实时消息发布器。
     *
     * @param messagingTemplate STOMP 消息模板；未启用 WebSocket 时可为空
     * @param sseConnectionRegistry SSE 本机连接注册表
     * @param sseMessagePublisher SSE 跨实例消息发布器
     * @param properties 实时通信配置
     */
    public DefaultRealtimePublisher(
            @Nullable SimpMessagingTemplate messagingTemplate,
            @Nullable SseConnectionRegistry sseConnectionRegistry,
            @Nullable SseMessagePublisher sseMessagePublisher,
            @NotNull Sca4djRealtimeProperties properties) {
        this.messagingTemplate = messagingTemplate;
        this.sseConnectionRegistry = sseConnectionRegistry;
        this.sseMessagePublisher = sseMessagePublisher;
        this.properties = properties;
    }

    @Override
    public void publish(@NotNull RealtimePublishRequest request) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(request);
                }
            });
            log.trace(
                    "实时消息已登记，等待事务提交 | event={} targetType={} transports={}",
                    request.getEvent(),
                    request.getTarget().getType(),
                    request.getTransports());
            return;
        }
        dispatch(request);
    }

    @Override
    public void publishNow(@NotNull RealtimePublishRequest request) {
        dispatch(request);
    }

    /**
     * 根据发布请求选择已启用的实时传输并立即发送。
     *
     * @param request 数据库事务已经提交后的发布请求
     */
    private void dispatch(@NotNull RealtimePublishRequest request) {
        RealtimeEnvelope<Object> envelope = new RealtimeEnvelope<>(
                request.getEvent(), request.getData(), request.getMetadata());
        boolean websocketSent = false;
        boolean sseSent = false;
        if (request.getTransports().contains(RealtimeTransport.WEBSOCKET)
                && properties.getWebsocket().isEnabled()) {
            sendWebSocket(request, envelope);
            websocketSent = true;
        }
        if (request.getTransports().contains(RealtimeTransport.SSE)
                && properties.getSse().isEnabled()) {
            sendSse(request, envelope);
            sseSent = true;
        }
        log.trace(
                "实时消息转发处理完成 | event={} targetType={} websocketSent={} sseSent={}",
                request.getEvent(),
                request.getTarget().getType(),
                websocketSent,
                sseSent);
    }

    /**
     * 通过 STOMP 发送实时信封。
     *
     * <p>带用户 destination 的请求只能定向投递给 Subject；普通请求按目标生成 broker destination。
     *
     * @param request 声明目标与可选用户 destination 的发布请求
     * @param envelope 本次发布共享的实时消息信封
     */
    private void sendWebSocket(@NotNull RealtimePublishRequest request, @NotNull RealtimeEnvelope<Object> envelope) {
        if (messagingTemplate == null) {
            throw new IllegalStateException("WebSocket 已启用但未注册 STOMP 消息模板");
        }
        String userDestination = request.getUserDestination();
        if (StringUtils.hasText(userDestination)) {
            messagingTemplate.convertAndSendToUser(userName(request.getTarget()), userDestination, envelope);
            return;
        }
        // 默认发送到 STOMP broker，WebSocket 客户端按目标 destination 订阅。
        messagingTemplate.convertAndSend(request.getTarget().stompDestination(), envelope);
    }

    /**
     * 将 Subject 目标转换为 Spring STOMP 用户标识。
     *
     * @param target 用户队列的 Subject 目标
     * @return 与握手 Principal 命名规则一致的用户标识
     * @throws IllegalArgumentException 目标不是 Subject 时抛出
     */
    private @NotNull String userName(@NotNull RealtimeTarget target) {
        if (target.getType() != RealtimeTargetType.SUBJECT) {
            throw new IllegalArgumentException("WebSocket 用户队列投递只支持 subject 目标");
        }
        return "subject:" + target.getValue();
    }

    /**
     * 通过可用的 SSE 传输发送实时信封。
     *
     * <p>集群发布器存在时由消息总线分发到各节点，否则直接发送给本机连接。配置声明 SSE 可用但未注册任一传输时明确失败，避免静默丢失消息。
     *
     * @param request 声明 SSE 目标的发布请求
     * @param envelope 本次发布共享的实时消息信封
     * @throws IllegalStateException SSE 已启用但没有可用传输时抛出
     */
    private void sendSse(@NotNull RealtimePublishRequest request, @NotNull RealtimeEnvelope<Object> envelope) {
        if (sseMessagePublisher != null) {
            sseMessagePublisher.publish(request.getTarget(), envelope);
            return;
        }
        if (sseConnectionRegistry != null) {
            sseConnectionRegistry.send(request.getTarget().routingKey(), envelope);
            return;
        }
        throw new IllegalStateException("SSE 已启用但未注册消息发布器或本机连接注册表");
    }
}
