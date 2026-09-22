package com.sca4d.messaging.rabbit;

import com.rabbitmq.client.Channel;
import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.messaging.EventHandler;
import com.sca4d.core.api.messaging.HandlingDecision;
import com.sca4d.core.api.messaging.IncomingMessage;
import com.sca4d.core.api.messaging.MessageHandler;
import com.sca4d.messaging.config.MessagingProperties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.context.SmartLifecycle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 为每个 MessageHandler 启动手动确认的竞争消费容器。 */
@ApiStatus.Internal
public final class RabbitHandlerContainers implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(RabbitHandlerContainers.class);

    private static final String ATTEMPT = "sca4d-handler-attempt";

    private final @NotNull RabbitMessageEnvelopeCodec codec;

    private final @NotNull RabbitMessageTopology topology;

    private final @NotNull RabbitPublisher publisher;

    private final @NotNull List<@NotNull SimpleMessageListenerContainer> containers;

    private volatile boolean running;

    /** 创建当前对象。 */
    public RabbitHandlerContainers(
            @NotNull ConnectionFactory connectionFactory,
            @NotNull RabbitMessageTopology topology,
            @NotNull RabbitMessageEnvelopeCodec codec,
            @NotNull MessagingProperties properties,
            @NotNull RabbitPublisher publisher,
            @NotNull List<? extends @NotNull EventHandler<?>> eventHandlers,
            @NotNull List<? extends @NotNull MessageHandler<?>> messageHandlers) {
        ConnectionFactory checkedFactory = Objects.requireNonNull(connectionFactory, "RabbitMQ 连接工厂不能为空");
        this.topology = Objects.requireNonNull(topology, "Messaging 拓扑不能为空");
        this.codec = Objects.requireNonNull(codec, "消息信封编解码器不能为空");
        this.publisher = Objects.requireNonNull(publisher, "RabbitMQ Publisher 不能为空");
        Objects.requireNonNull(properties, "Messaging 配置不能为空");
        List<SimpleMessageListenerContainer> result = new ArrayList<>();
        for (RabbitHandlerDescriptor handler : topology.descriptors(eventHandlers, messageHandlers)) {
            SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(checkedFactory);
            container.setQueueNames(topology.queueName(handler));
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
    public boolean isAutoStartup() {
        return true;
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
        int attempt = attempt(rabbitMessage);
        String messageId = rabbitMessage.getMessageProperties().getMessageId();
        HandlingDecision decision;
        try {
            decision = handler.event()
                    ? handleEvent(handler, rabbitMessage, attempt)
                    : handleMessage(handler, rabbitMessage, attempt);
        } catch (Exception exception) {
            decision = attempt < handler.handlingPolicy().maximumAttempts()
                    ? HandlingDecision.retry(exception.getClass().getSimpleName())
                    : HandlingDecision.reject(exception.getClass().getSimpleName());
            LOG.error(
                    "业务消息处理异常 | handlerCode={} messageId={} messageCode={} attempt={}",
                    handler.code(),
                    messageId,
                    handler.messageCode(),
                    attempt,
                    exception);
        }

        switch (decision.status()) {
            case ACKNOWLEDGE -> acknowledge(channel, deliveryTag, handler, messageId, attempt);
            case REJECT -> reject(channel, deliveryTag, handler, messageId, attempt, decision.detail());
            case RETRY -> retry(channel, deliveryTag, handler, rabbitMessage, messageId, attempt, decision.detail());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private HandlingDecision handleEvent(
            RabbitHandlerDescriptor handler,
            Message rabbitMessage,
            int attempt) throws Exception {
        EventHandler eventHandler = handler.eventHandler();
        IncomingMessage<Event<Object>> incoming = codec.decodeEvent(
                rabbitMessage.getBody(),
                eventHandler.eventType(),
                rabbitMessage.getMessageProperties().isRedelivered(),
                attempt);
        return Objects.requireNonNull(eventHandler.handle(incoming), "EventHandler 不能返回 null");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private HandlingDecision handleMessage(
            RabbitHandlerDescriptor handler,
            Message rabbitMessage,
            int attempt) throws Exception {
        MessageHandler messageHandler = handler.messageHandler();
        IncomingMessage<?> incoming = codec.decode(
                rabbitMessage.getBody(),
                messageHandler.codec(),
                rabbitMessage.getMessageProperties().isRedelivered(),
                attempt);
        return Objects.requireNonNull(messageHandler.handle(incoming), "MessageHandler 不能返回 null");
    }

    private void acknowledge(
            Channel channel,
            long deliveryTag,
            RabbitHandlerDescriptor handler,
            String messageId,
            int attempt) {
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
            int attempt,
            String detail) {
        try {
            channel.basicReject(deliveryTag, false);
        } catch (IOException exception) {
            throw new AmqpIOException(exception);
        }
        LOG.warn(
                "业务消息已送入 Handler DLQ | handlerCode={} messageId={} messageCode={} attempt={} detail={}",
                handler.code(),
                messageId,
                handler.messageCode(),
                attempt,
                detail);
    }

    private void retry(
            Channel channel,
            long deliveryTag,
            RabbitHandlerDescriptor handler,
            Message original,
            String messageId,
            int attempt,
            String detail) {
        if (attempt >= handler.handlingPolicy().maximumAttempts()) {
            reject(channel, deliveryTag, handler, messageId, attempt, detail);
            return;
        }
        Message retry = MessageBuilder.fromMessage(original)
                .setHeader(ATTEMPT, attempt + 1)
                .build();
        try {
            publisher.sendRetry(handler.code(), retry, attempt + 1);
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException | IOException exception) {
            try {
                channel.basicNack(deliveryTag, false, true);
            } catch (IOException nackException) {
                exception.addSuppressed(nackException);
                throw new AmqpIOException(nackException);
            }
            throw exception instanceof RuntimeException runtimeException
                    ? runtimeException
                    : new AmqpIOException((IOException) exception);
        }
        LOG.warn(
                "业务消息等待 Handler 再次处理 | handlerCode={} messageId={} messageCode={} attempt={} detail={}",
                handler.code(),
                messageId,
                handler.messageCode(),
                attempt,
                detail);
    }

    private static int attempt(Message message) {
        Object value = message.getMessageProperties().getHeaders().get(ATTEMPT);
        if (value instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        return 1;
    }
}
