package com.sca4d.common.messaging.rabbit;

import com.sca4d.common.messaging.MessageEnvelope;
import com.sca4d.common.messaging.MessagePublishException;
import com.sca4d.config.properties.Sca4djMessagingProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * RabbitMQ 消息发送器。
 */
public class RabbitMessageSender {

    private static final String HEADER_MESSAGE_ID = "x-message-id";
    private static final String HEADER_EVENT = "x-event";
    private static final String HEADER_BUSINESS_KEY = "x-business-key";
    private static final String HEADER_TRACE_ID = "x-trace-id";
    private static final String HEADER_SOURCE = "x-source";

    private final RabbitTemplate rabbitTemplate;
    private final Sca4djMessagingProperties properties;

    /**
     * 创建 RabbitMQ 消息发送器。
     *
     * @param rabbitTemplate RabbitMQ 模板
     * @param properties 消息发布配置
     */
    public RabbitMessageSender(
            @NotNull RabbitTemplate rabbitTemplate, @NotNull Sca4djMessagingProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.rabbitTemplate.setMandatory(true);
    }

    /**
     * 发送消息并等待 broker confirm。
     *
     * @param exchange exchange 名称
     * @param routingKey routing key
     * @param envelope 消息信封
     * @param delay 延迟时间
     */
    public void send(
            @NotNull String exchange,
            @NotNull String routingKey,
            @NotNull MessageEnvelope<?> envelope,
            @NotNull Duration delay) {
        if (!delay.isZero() && !delay.isNegative() && !properties.getRabbit().isDelayedExchangeEnabled()) {
            throw new MessagePublishException("当前 RabbitMQ 未启用 delayed exchange，不能直接发送延迟消息");
        }
        Integer delayMillis = delay.isZero() || delay.isNegative() ? null : Math.toIntExact(delay.toMillis());
        CorrelationData correlationData = new CorrelationData(envelope.getMessageId());
        try {
            rabbitTemplate.convertAndSend(
                    exchange,
                    routingKey,
                    envelope,
                    message -> {
                        MessageProperties messageProperties = message.getMessageProperties();
                        messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        messageProperties.setMessageId(envelope.getMessageId());
                        messageProperties.setType(envelope.getEvent());
                        if (envelope.getPublishedAt() != null) {
                            messageProperties.setTimestamp(Date.from(envelope.getPublishedAt().toInstant()));
                        }
                        messageProperties.getHeaders().putAll(envelope.getHeaders());
                        messageProperties.setHeader(HEADER_MESSAGE_ID, envelope.getMessageId());
                        messageProperties.setHeader(HEADER_EVENT, envelope.getEvent());
                        messageProperties.setHeader(HEADER_SOURCE, envelope.getSource());
                        if (StringUtils.hasText(envelope.getBusinessKey())) {
                            messageProperties.setHeader(HEADER_BUSINESS_KEY, envelope.getBusinessKey());
                        }
                        if (StringUtils.hasText(envelope.getTraceId())) {
                            messageProperties.setHeader(HEADER_TRACE_ID, envelope.getTraceId());
                        }
                        if (delayMillis != null) {
                            messageProperties.setHeader(MessageProperties.X_DELAY, delayMillis);
                        }
                        return message;
                    },
                    correlationData);
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(properties.getRabbit().getConfirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new MessagePublishException("RabbitMQ confirm 返回 nack");
            }
            if (correlationData.getReturned() != null) {
                throw new MessagePublishException("RabbitMQ 消息不可路由");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MessagePublishException("RabbitMQ confirm 等待被中断", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new MessagePublishException("RabbitMQ confirm 等待失败", exception);
        } catch (MessageConversionException exception) {
            throw new MessagePublishException("消息信封序列化失败", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof MessagePublishException publishException) {
                throw publishException;
            }
            throw new MessagePublishException("RabbitMQ 消息发送失败", exception);
        }
    }
}
