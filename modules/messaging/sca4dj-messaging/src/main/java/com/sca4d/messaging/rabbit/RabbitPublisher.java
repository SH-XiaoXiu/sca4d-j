package com.sca4d.messaging.rabbit;

import com.sca4d.core.api.code.TreeCode;
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

/** 使用 mandatory routing 和 publisher confirm 同步判断 RabbitMQ 发布是否成功。 */
@ApiStatus.Internal
public final class RabbitPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(RabbitPublisher.class);

    private final @NotNull RabbitTemplate rabbit;

    private final @NotNull String exchange;

    private final @NotNull String retryExchange;

    private final @NotNull Duration confirmTimeout;

    /** 创建向业务消息 exchange 和 Handler retry exchange 发布持久消息的操作。 */
    public RabbitPublisher(
            @NotNull RabbitTemplate rabbit,
            @NotNull RabbitMessageTopology topology,
            @NotNull MessagingProperties properties) {
        this.rabbit = Objects.requireNonNull(rabbit, "RabbitTemplate 不能为空");
        this.rabbit.setMandatory(true);
        RabbitMessageTopology checkedTopology = Objects.requireNonNull(topology, "Messaging 拓扑不能为空");
        this.exchange = checkedTopology.exchangeName();
        this.retryExchange = checkedTopology.retryExchangeName();
        this.confirmTimeout = Objects.requireNonNull(properties, "Messaging 配置不能为空").getConfirmTimeout();
    }

    /**
     * 发布持久业务消息，并等待 mandatory return 与 publisher confirm。
     *
     * @throws IllegalStateException 消息没有路由、代理拒绝、确认超时或等待被中断时抛出
     */
    public void send(
            @NotNull MessageId messageId,
            @NotNull TreeCode messageCode,
            byte @NotNull [] envelope) {
        MessageId checkedId = Objects.requireNonNull(messageId, "MessageId 不能为空");
        TreeCode checkedCode = Objects.requireNonNull(messageCode, "消息编码不能为空");
        Message rabbitMessage = MessageBuilder
                .withBody(Objects.requireNonNull(envelope, "消息信封不能为空"))
                .setContentType("application/json")
                .setContentEncoding("UTF-8")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(checkedId.toString())
                .build();
        send(exchange, checkedCode.value(), rabbitMessage, checkedId.toString());
        LOG.trace("业务消息已由 RabbitMQ 确认 | messageId={} messageCode={}", checkedId, checkedCode);
    }

    /**
     * 把 Handler 请求再次处理的原消息发布到对应 retry queue，并在确认成功后返回。
     *
     * @throws IllegalStateException retry queue 不存在、代理拒绝、确认超时或等待被中断时抛出
     */
    public void sendRetry(@NotNull TreeCode handlerCode, @NotNull Message message, int attempt) {
        if (attempt < 2) {
            throw new IllegalArgumentException("Handler retry 尝试序号必须至少为 2");
        }
        TreeCode checkedCode = Objects.requireNonNull(handlerCode, "Handler 编码不能为空");
        Message checkedMessage = Objects.requireNonNull(message, "Handler retry 消息不能为空");
        String messageId = checkedMessage.getMessageProperties().getMessageId();
        String correlationId = (messageId == null ? "UNKNOWN" : messageId)
                + ':' + checkedCode.value() + ':' + attempt;
        send(retryExchange, checkedCode.value(), checkedMessage, correlationId);
    }

    private void send(
            @NotNull String targetExchange,
            @NotNull String routingKey,
            @NotNull Message message,
            @NotNull String correlationId) {
        CorrelationData correlation = new CorrelationData(correlationId);
        rabbit.send(targetExchange, routingKey, message, correlation);
        try {
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException("RabbitMQ 拒绝消息发布: " + confirm.getReason());
            }
            if (correlation.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ 没有匹配消息路由");
            }
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
