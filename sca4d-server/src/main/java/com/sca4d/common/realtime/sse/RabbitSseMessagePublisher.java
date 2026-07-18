package com.sca4d.common.realtime.sse;

import com.sca4d.common.messaging.MessageCommand;
import com.sca4d.common.messaging.MessagePublishMode;
import com.sca4d.common.messaging.MessagePublisher;
import com.sca4d.common.realtime.RealtimeEnvelope;
import com.sca4d.common.realtime.RealtimeTarget;
import com.sca4d.config.properties.Sca4djRealtimeProperties;
import org.jetbrains.annotations.NotNull;

/**
 * 基于共用消息发布器的 RabbitMQ SSE 跨实例消息发布器。
 */
public class RabbitSseMessagePublisher implements SseMessagePublisher {

    private final MessagePublisher messagePublisher;
    private final Sca4djRealtimeProperties properties;

    /**
     * 创建 RabbitMQ SSE 跨实例消息发布器。
     *
     * @param messagePublisher 共用消息发布器
     * @param properties 实时通信配置
     */
    public RabbitSseMessagePublisher(
            @NotNull MessagePublisher messagePublisher, @NotNull Sca4djRealtimeProperties properties) {
        this.messagePublisher = messagePublisher;
        this.properties = properties;
    }

    @Override
    public void publish(@NotNull RealtimeTarget target, @NotNull RealtimeEnvelope<?> envelope) {
        // DefaultRealtimePublisher 只会在事务提交后调用这里；此处直接发送，不能再次注册 afterCommit 回调。
        MessageCommand command = MessageCommand.of(
                properties.getSse().getExchange(),
                target.routingKey(),
                envelope.getEvent(),
                envelope)
                .setMode(MessagePublishMode.DIRECT)
                .setMessageId(envelope.getId())
                .setBusinessKey("sse:" + envelope.getId());
        messagePublisher.publish(command);
    }
}
