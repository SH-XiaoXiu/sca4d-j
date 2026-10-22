package com.sca4d.messaging.rabbit;

import static com.sca4d.core.api.name.NameFormat.COLON;

import com.sca4d.core.api.codec.Codec;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.retry.RetryRule;
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
public class RabbitPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(RabbitPublisher.class);

    private final @NotNull Codec<RetryRule, String> retryRules;

    private final @NotNull RabbitTemplate rabbit;

    private final @NotNull Key exchange;

    static final TreeCode ATTEMPT = TreeCode.ofColon("HANDLER:ATTEMPT");

    static final TreeCode RETRY_RULE = TreeCode.ofColon("HANDLER:RETRY_RULE");

    private final @NotNull RabbitMessageTopology topology;

    private final @NotNull Duration confirmTimeout;

    /** 创建向业务消息 exchange 和 Handler 主队列 发布持久消息的操作。 */
    public RabbitPublisher(
            @NotNull RabbitTemplate rabbit,
            @NotNull RabbitMessageTopology topology,
            @NotNull MessagingProperties properties,
            @NotNull Codec<RetryRule, String> retryRules) {
        this.rabbit = Objects.requireNonNull(rabbit, "RabbitTemplate 不能为空");
        this.retryRules = Objects.requireNonNull(retryRules);
        this.rabbit.setMandatory(true);
        RabbitMessageTopology checkedTopology = Objects.requireNonNull(topology, "Messaging 拓扑不能为空");
        this.topology = checkedTopology;
        this.exchange = checkedTopology.exchangeName();
        this.confirmTimeout = Objects.requireNonNull(properties, "Messaging 配置不能为空").getConfirmTimeout();
    }

    /**
     * 发布持久业务消息，并等待 mandatory return 与 publisher confirm。
     *
     * @throws IllegalStateException 消息没有路由、代理拒绝、确认超时或等待被中断时抛出
     */
    public void send(
            @NotNull Id messageId,
            @NotNull TreeCode messageCode,
            byte @NotNull [] envelope) {
        Id checkedId = Objects.requireNonNull(messageId, "Id 不能为空");
        TreeCode checkedCode = Objects.requireNonNull(messageCode, "消息编码不能为空");
        Message rabbitMessage = MessageBuilder
                .withBody(Objects.requireNonNull(envelope, "消息信封不能为空"))
                .setContentType("application/json")
                .setContentEncoding("UTF-8")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(checkedId.toString())
                .build();
        send(exchange.format(COLON), topology.routingKey(checkedCode), rabbitMessage, checkedId.toString());
        LOG.trace("业务消息已由 RabbitMQ 确认 | messageId={} messageCode={}", checkedId, checkedCode);
    }

    /** 把原始信封定向发布到一个 Handler 主队列，代理确认后才返回。 */
    public void sendHandler(
            @NotNull Id outboxId,
            @NotNull Id messageId,
            @NotNull TreeCode handlerCode,
            byte @NotNull [] envelope,
            long attempt,
            @NotNull RetryRule retryRule) {
        if (attempt < 2)
            throw new IllegalArgumentException("再次消费序号必须至少为 2");
        Message message = MessageBuilder.withBody(envelope)
                .setContentType("application/json")
                .setContentEncoding("UTF-8")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(messageId.toString())
                .setHeader(ATTEMPT.format(COLON), attempt)
                .setHeader(RETRY_RULE.format(COLON), retryRules.encode(retryRule))
                .build();
        send("", topology.handlerQueueName(handlerCode), message, outboxId.toString());
    }

    private void send(
            @NotNull String targetExchange,
            @NotNull Key routingKey,
            @NotNull Message message,
            @NotNull String correlationId) {
        CorrelationData correlation = new CorrelationData(correlationId);
        rabbit.send(targetExchange, routingKey.format(COLON), message, correlation);
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
