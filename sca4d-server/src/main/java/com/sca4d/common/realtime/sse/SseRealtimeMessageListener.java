package com.sca4d.common.realtime.sse;

import com.sca4d.common.exception.InternalServiceException;
import com.sca4d.common.messaging.MessageEnvelope;
import com.sca4d.common.realtime.RealtimeEnvelope;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.Message;
import org.springframework.util.StringUtils;

/**
 * SSE RabbitMQ 消息监听器。
 */
public class SseRealtimeMessageListener {

    private static final Logger log = LoggerFactory.getLogger(SseRealtimeMessageListener.class);

    private final SseConnectionRegistry registry;

    /**
     * 创建 SSE RabbitMQ 消息监听器。
     *
     * @param registry SSE 本机连接注册表
     */
    public SseRealtimeMessageListener(@NotNull SseConnectionRegistry registry) {
        this.registry = registry;
    }

    /**
     * 接收 RabbitMQ SSE 跨实例消息。
     *
     * @param message 标准消息信封
     */
    @RabbitListener(id = "sseRealtimeMessageListener", bindings = @QueueBinding(value = @Queue(durable = "false", exclusive = "true", autoDelete = "true"), exchange = @Exchange(value = "${sca4dj.realtime.sse.exchange:sca4dj.realtime.sse}", type = ExchangeTypes.TOPIC, durable = "true"), key = "#"))
    public void onMessage(@NotNull Message<MessageEnvelope<RealtimeEnvelope<Object>>> message) {
        String routingKey = message.getHeaders().get(AmqpHeaders.RECEIVED_ROUTING_KEY, String.class);
        if (!StringUtils.hasText(routingKey)) {
            log.warn("SSE 实时消息缺少路由标识，当前消息不转发 | messageId={}",
                    message.getPayload().getMessageId());
            return;
        }
        RealtimeEnvelope<Object> envelope = message.getPayload().getPayload();
        if (envelope == null) {
            throw new InternalServiceException("SSE 消息缺少实时载荷");
        }
        registry.send(routingKey, envelope);
        log.trace("SSE 实时消息本机处理完成 | messageId={} event={} routingKey={}",
                message.getPayload().getMessageId(), envelope.getEvent(), routingKey);
    }
}
