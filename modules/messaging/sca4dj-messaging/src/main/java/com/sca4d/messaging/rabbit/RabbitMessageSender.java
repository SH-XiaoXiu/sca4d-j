package com.sca4d.messaging.rabbit;

import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.messaging.config.MessagingProperties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 使用 mandatory routing 和 publisher confirm 同步判断一次 RabbitMQ 发布是否成功。 */
@ApiStatus.Internal
public final class RabbitMessageSender {

    private static final Logger LOG = LoggerFactory.getLogger(RabbitMessageSender.class);

    private final @NotNull RabbitTemplate rabbit;

    private final @NotNull String exchange;

    private final @NotNull Duration confirmTimeout;

    /** 创建向业务消息 exchange 发布持久消息的操作。 */
    public RabbitMessageSender(
            @NotNull RabbitTemplate rabbit,
            @NotNull MessagingTopology topology,
            @NotNull MessagingProperties properties) {
        this.rabbit = Objects.requireNonNull(rabbit, "RabbitTemplate 不能为空");
        this.rabbit.setMandatory(true);
        this.exchange = Objects.requireNonNull(topology, "Messaging 拓扑不能为空").exchangeName();
        this.confirmTimeout = Objects.requireNonNull(properties, "Messaging 配置不能为空")
                .getConfirmTimeout();
    }

    /**
     * 发布持久消息，并等待 mandatory return 与 publisher confirm 的最终结果。
     *
     * @throws IllegalStateException 消息没有路由、代理拒绝、确认超时或等待被中断时抛出
     */
    public void send(
            @NotNull MessageId messageId,
            @NotNull String eventCode,
            byte @NotNull [] envelope) {
        MessageId checkedId = Objects.requireNonNull(messageId, "MessageId 不能为空");
        Message rabbitMessage = MessageBuilder
                .withBody(Objects.requireNonNull(envelope, "消息信封不能为空"))
                .setContentType("application/json")
                .setContentEncoding("UTF-8")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(checkedId.toString())
                .build();
        CorrelationData correlation = new CorrelationData(checkedId.toString());

        rabbit.send(exchange, Objects.requireNonNull(eventCode, "事件编码不能为空"), rabbitMessage, correlation);
        try {
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(
                            confirmTimeout.toMillis(),
                            TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException("RabbitMQ 拒绝消息发布: " + confirm.getReason());
            }
            if (correlation.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ 没有匹配消息路由");
            }
            LOG.trace("业务消息已由 RabbitMQ 确认 | messageId={} eventCode={}", checkedId, eventCode);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 RabbitMQ 发布确认时被中断", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("RabbitMQ 发布确认失败", exception.getCause());
        } catch (TimeoutException exception) {
            throw new IllegalStateException("等待 RabbitMQ 发布确认超时", exception);
        }
    }
}
