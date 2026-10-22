package com.sca4d.messaging.rabbit;

import static com.sca4d.core.api.name.NameFormat.COLON;

import com.rabbitmq.client.Channel;
import com.sca4d.core.api.codec.Codec;
import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.messaging.EventHandler;
import com.sca4d.core.api.messaging.HandlingDecision;
import com.sca4d.core.api.messaging.IncomingMessage;
import com.sca4d.core.api.messaging.MessageHandler;
import com.sca4d.core.api.retry.RetryContext;
import com.sca4d.core.api.retry.RetryDecision;
import com.sca4d.core.api.retry.RetryPolicy;
import com.sca4d.core.api.retry.RetryRule;
import com.sca4d.core.api.retry.RetryStopReason;
import com.sca4d.messaging.outbox.MessageOutboxRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.context.SmartLifecycle;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 为每个 MessageHandler 启动手动确认的竞争消费容器。 */
@ApiStatus.Internal
public class RabbitHandlerContainers implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(RabbitHandlerContainers.class);

    private final @NotNull MessageOutboxRepository outbox;

    private final @NotNull Clock clock;

    private final Codec<RetryRule, String> rules;

    private final @NotNull IdGenerator ids;

    private final @NotNull RabbitMessageEnvelopeCodec codec;

    private final @NotNull TransactionTemplate transactions;

    private final @NotNull List<@NotNull SimpleMessageListenerContainer> containers;

    private volatile boolean running;

    /**
     * @param connectionFactory RabbitMQ 连接工厂
     *
     * @param topology 消息拓扑
     * @param codec 消息信封 Codec
     * @param outbox 定向重试交接入口
     * @param ids 投递记录标识生成器
     * @param transactionManager 独立交接事务管理器
     * @param clock 下次投递时间使用的时钟
     * @param rules 由脚手架 JSON 装配提供的重试规则 Codec
     * @param eventHandlers 已安装的 EventHandler
     * @param messageHandlers 已安装的 MessageHandler
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Outbox Repository 是注入的共享协作对象，容器不拥有其内部表示")
    public RabbitHandlerContainers(
            @NotNull ConnectionFactory connectionFactory,
            @NotNull RabbitMessageTopology topology,
            @NotNull RabbitMessageEnvelopeCodec codec,
            @NotNull MessageOutboxRepository outbox,
            @NotNull IdGenerator ids,
            @NotNull PlatformTransactionManager transactionManager,
            @NotNull Clock clock,
            @NotNull Codec<RetryRule, String> rules,
            @NotNull List<? extends @NotNull EventHandler<?>> eventHandlers,
            @NotNull List<? extends @NotNull MessageHandler<?>> messageHandlers) {
        ConnectionFactory checkedFactory = Objects.requireNonNull(connectionFactory, "RabbitMQ 连接工厂不能为空");
        RabbitMessageTopology checkedTopology = Objects.requireNonNull(topology, "Messaging 拓扑不能为空");
        this.codec = Objects.requireNonNull(codec, "消息信封编解码器不能为空");
        this.outbox = Objects.requireNonNull(outbox);
        this.ids = Objects.requireNonNull(ids);
        this.clock = Objects.requireNonNull(clock);
        this.rules = Objects.requireNonNull(rules);
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        List<SimpleMessageListenerContainer> result = new ArrayList<>();
        for (RabbitHandlerDescriptor handler : checkedTopology.descriptors(eventHandlers, messageHandlers)) {
            SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(checkedFactory);
            container.setQueueNames(checkedTopology.handlerQueueName(handler.code()).format(COLON));
            container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
            container.setDefaultRequeueRejected(false);
            container.setAutoStartup(false);
            container.setMessageListener((ChannelAwareMessageListener) (message, channel) -> {
                if (channel == null) {
                    throw new IllegalStateException("RabbitMQ Handler 没有可用 Channel");
                }
                handle(handler, message, channel);
            });
            result.add(container);
        }
        this.containers = List.copyOf(result);
    }

    @Override
    public void start() {
        containers.forEach(SimpleMessageListenerContainer::start);
        running = true;
    }

    @Override
    public void stop() {
        containers.forEach(SimpleMessageListenerContainer::stop);
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return 0;
    }

    private void handle(
            @NotNull RabbitHandlerDescriptor handler,
            @NotNull Message rabbitMessage,
            @NotNull Channel channel) {
        long deliveryTag = rabbitMessage.getMessageProperties().getDeliveryTag();
        long attempt;
        Id id;
        RetryRule rule;
        String messageId = rabbitMessage.getMessageProperties().getMessageId();
        // 信封与重试元数据先校验，不能把损坏次数重置为首次执行。
        try {
            attempt = attempt(rabbitMessage);
            id = codec.messageId(rabbitMessage.getBody(), handler.messageCode());
            if (!id.toString().equals(messageId))
                throw new IllegalArgumentException("AMQP 消息标识与信封不一致");
            Object snapshot = rabbitMessage.getMessageProperties()
                    .getHeaders()
                    .get(RabbitPublisher.RETRY_RULE.format(COLON));
            rule = snapshot == null ? handler.retryRule() : rules.decode(snapshot.toString());
        } catch (IllegalArgumentException failure) {
            reject(channel, deliveryTag, handler, messageId, 0, "INVALID_ENVELOPE_OR_RETRY_METADATA");
            return;
        }
        // 已提交的交接记录可确认原消息；数据库不可用时保留 Broker 消息。
        try {
            if (attempt < Long.MAX_VALUE
                    && outbox.hasHandlerRetry(handler.code(), id, attempt + 1, rabbitMessage.getBody())) {
                acknowledge(channel, deliveryTag, handler, messageId, attempt);
                return;
            }
        } catch (IllegalArgumentException failure) {
            reject(channel, deliveryTag, handler, messageId, attempt, "INCONSISTENT_RETRY_ENVELOPE");
            return;
        } catch (RuntimeException failure) {
            requeue(channel, deliveryTag, failure);
            return;
        }
        HandlingDecision decision;
        try {
            decision = handler.event()
                    ? handleEvent(handler, rabbitMessage, attempt)
                    : handleMessage(handler, rabbitMessage, attempt);
        } catch (Exception exception) {
            decision = HandlingDecision.retry(exception.getClass().getSimpleName());
            LOG.warn(
                    "业务消息处理失败，将按规则交接或拒绝 | handlerCode={} messageId={} attempt={} failureType={}",
                    handler.code(),
                    messageId,
                    attempt,
                    exception.getClass().getSimpleName());
        }
        switch (decision.status()) {
            case ACKNOWLEDGE -> acknowledge(channel, deliveryTag, handler, messageId, attempt);
            case REJECT -> reject(channel, deliveryTag, handler, messageId, attempt, decision.detail());
            case RETRY -> retry(
                    channel,
                    deliveryTag,
                    handler,
                    rabbitMessage,
                    id,
                    Math.max(attempt, decision.completedAttempts()),
                    rule,
                    decision.detail());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private HandlingDecision handleEvent(
            RabbitHandlerDescriptor handler,
            Message rabbitMessage,
            long attempt) throws Exception {
        EventHandler eventHandler = handler.eventHandler();
        IncomingMessage<Event<Object>> incoming;
        try {
            incoming = codec.decodeEvent(
                    rabbitMessage.getBody(),
                    eventHandler.eventType(),
                    rabbitMessage.getMessageProperties().isRedelivered(),
                    attempt);
        } catch (IllegalArgumentException failure) {
            return HandlingDecision.reject("INVALID_MESSAGE");
        }
        return Objects.requireNonNull(eventHandler.handle(incoming), "EventHandler 不能返回 null");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private HandlingDecision handleMessage(
            RabbitHandlerDescriptor handler,
            Message rabbitMessage,
            long attempt) throws Exception {
        MessageHandler messageHandler = handler.messageHandler();
        IncomingMessage<?> incoming;
        try {
            incoming = codec.decode(
                    rabbitMessage.getBody(),
                    messageHandler.codec(),
                    rabbitMessage.getMessageProperties().isRedelivered(),
                    attempt);
        } catch (IllegalArgumentException failure) {
            return HandlingDecision.reject("INVALID_MESSAGE");
        }
        return Objects.requireNonNull(messageHandler.handle(incoming), "MessageHandler 不能返回 null");
    }

    private void acknowledge(
            Channel channel,
            long deliveryTag,
            RabbitHandlerDescriptor handler,
            String messageId,
            long attempt) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException exception) {
            throw new AmqpIOException(exception);
        }
        LOG.trace(
                "业务消息处理完成 | handlerCode={} messageId={} messageCode={} attempt={}",
                handler.code(),
                messageId,
                handler.messageCode(),
                attempt);
    }

    private void reject(
            Channel channel,
            long deliveryTag,
            RabbitHandlerDescriptor handler,
            String messageId,
            long attempt,
            String detail) {
        try {
            channel.basicReject(deliveryTag, false);
        } catch (IOException exception) {
            throw new AmqpIOException(exception);
        }
        if (RetryStopReason.ATTEMPTS_EXHAUSTED.name().equals(detail)) {
            LOG.error(
                    "业务消息重试耗尽，已送入 Handler DLQ | handlerCode={} messageId={} messageCode={} attempt={}",
                    handler.code(),
                    messageId,
                    handler.messageCode(),
                    attempt);
        } else {
            LOG.warn(
                    "业务消息已送入 Handler DLQ | handlerCode={} messageId={} messageCode={} attempt={} detail={}",
                    handler.code(),
                    messageId,
                    handler.messageCode(),
                    attempt,
                    detail);
        }
    }

    private void retry(
            Channel channel,
            long deliveryTag,
            RabbitHandlerDescriptor handler,
            Message original,
            Id messageId,
            long attempt,
            RetryRule rule,
            String detail) {
        RetryDecision decision = RetryPolicy.always(rule)
                .evaluate(
                        RetryContext.failed(
                                attempt,
                                detail == null ? "HANDLER_FAILURE" : detail));
        if (!decision.retrying()) {
            reject(
                    channel,
                    deliveryTag,
                    handler,
                    messageId.toString(),
                    attempt,
                    decision.stopReason().orElseThrow().name());
            return;
        }
        Instant availableAt = clock.instant().plus(decision.retryDelay().orElseThrow());
        try {
            // 交接独立提交后才确认原消息；发布失败由 Outbox 的发布次数处理。
            transactions.executeWithoutResult(
                    status -> outbox.scheduleHandlerRetry(
                            ids.nextId(),
                            messageId,
                            handler.messageCode(),
                            original.getBody(),
                            handler.code(),
                            Math.incrementExact(attempt),
                            rule,
                            availableAt));
            acknowledge(channel, deliveryTag, handler, messageId.toString(), attempt);
        } catch (IllegalArgumentException failure) {
            reject(channel, deliveryTag, handler, messageId.toString(), attempt, "INCONSISTENT_RETRY_ENVELOPE");
            return;
        } catch (RuntimeException failure) {
            requeue(channel, deliveryTag, failure);
            return;
        }
        LOG.warn(
                "业务消息已交接等待再次处理 | handlerCode={} messageId={} attempt={} nextAttemptAt={}",
                handler.code(),
                messageId,
                attempt,
                availableAt);
    }

    /** 交接失败时保留原 Broker 消息，不能按业务拒绝丢入 DLQ。 */
    private static void requeue(Channel channel, long deliveryTag, RuntimeException failure) {
        try {
            channel.basicNack(deliveryTag, false, true);
        } catch (IOException nackFailure) {
            failure.addSuppressed(nackFailure);
            throw failure;
        }
        LOG.warn("消息重试交接无法确认，原消息保留等待再次交付 | failureType={}", failure.getClass().getSimpleName());
    }

    private static long attempt(Message message) {
        Object value = message.getMessageProperties().getHeaders().get(RabbitPublisher.ATTEMPT.format(COLON));
        if (value == null)
            return 1;
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            long attempt = ((Number) value).longValue();
            if (attempt > 0)
                return attempt;
        }
        throw new IllegalArgumentException("Handler 尝试序号必须为正整数");
    }
}
