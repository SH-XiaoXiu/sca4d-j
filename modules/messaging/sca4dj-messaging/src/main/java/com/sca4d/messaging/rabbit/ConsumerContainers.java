package com.sca4d.messaging.rabbit;

import com.rabbitmq.client.Channel;
import com.sca4d.core.api.messaging.MessageConsumer;
import com.sca4d.core.api.messaging.MessageDelivery;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.context.SmartLifecycle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 为每个 MessageConsumer 启动竞争消费容器，并把任意消费失败直接拒绝到该 Consumer 的 DLQ。 */
@ApiStatus.Internal
public final class ConsumerContainers implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(ConsumerContainers.class);

    private final @NotNull MessagingEnvelopeCodec codec;

    private final @NotNull List<@NotNull SimpleMessageListenerContainer> containers;

    private volatile boolean running;

    /** 创建使用手动 ACK 且失败不重新入队的 Consumer 容器。 */
    public ConsumerContainers(
            @NotNull ConnectionFactory connectionFactory,
            @NotNull MessagingTopology topology,
            @NotNull MessagingEnvelopeCodec codec,
            @NotNull List<@NotNull MessageConsumer<?>> consumers) {
        ConnectionFactory checkedFactory = Objects.requireNonNull(connectionFactory, "RabbitMQ 连接工厂不能为空");
        MessagingTopology checkedTopology = Objects.requireNonNull(topology, "Messaging 拓扑不能为空");
        this.codec = Objects.requireNonNull(codec, "消息信封编解码器不能为空");
        List<SimpleMessageListenerContainer> result = new ArrayList<>();
        for (MessageConsumer<?> consumer : checkedTopology.validatedConsumers(consumers)) {
            SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(checkedFactory);
            container.setQueueNames(checkedTopology.queueName(consumer));
            container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
            container.setDefaultRequeueRejected(false);
            container.setAutoStartup(false);
            container.setMessageListener((ChannelAwareMessageListener) (message, channel) -> {
                if (channel == null) {
                    throw new IllegalStateException("RabbitMQ Consumer 没有可用 Channel");
                }
                consume(consumer, message, channel);
            });
            result.add(container);
        }
        this.containers = List.copyOf(result);
    }

    /** 启动全部 Consumer 容器。 */
    @Override
    public void start() {
        containers.forEach(SimpleMessageListenerContainer::start);
        running = true;
    }

    /** 停止全部 Consumer 容器。 */
    @Override
    public void stop() {
        containers.forEach(SimpleMessageListenerContainer::stop);
        running = false;
    }

    /** @return 至少已执行启动且尚未停止时返回 true */
    @Override
    public boolean isRunning() {
        return running;
    }

    /** @return 由 Spring 自动启动 */
    @Override
    public boolean isAutoStartup() {
        return true;
    }

    /** @return 在普通应用生命周期组件阶段启动 */
    @Override
    public int getPhase() {
        return 0;
    }

    /** 按具体 payload 类型解码、调用 Consumer，并明确 ACK 或拒绝。 */
    private <T> void consume(
            @NotNull MessageConsumer<T> consumer,
            @NotNull org.springframework.amqp.core.Message rabbitMessage,
            @NotNull Channel channel) {
        long deliveryTag = rabbitMessage.getMessageProperties().getDeliveryTag();
        String messageId = rabbitMessage.getMessageProperties().getMessageId();
        try {
            // 严格校验 AMQP 属性和信封，再按当前 Consumer 的 EventType 恢复 payload 并执行完整消费动作。
            MessageDelivery<T> delivery = codec.decode(
                    rabbitMessage.getBody(),
                    consumer.eventType(),
                    rabbitMessage.getMessageProperties().isRedelivered());
            String envelopeMessageId = delivery.messageId().toString();
            if (!envelopeMessageId.equals(messageId)) {
                throw new IllegalArgumentException("RabbitMQ message-id 与业务消息信封 MessageId 不一致");
            }
            messageId = envelopeMessageId;
            consumer.consume(delivery);
        } catch (Exception exception) {
            // 业务失败和协议失败都不自动重试，直接拒绝到当前 Consumer 的独立 DLQ。
            try {
                channel.basicReject(deliveryTag, false);
            } catch (IOException rejectException) {
                rejectException.addSuppressed(exception);
                throw new AmqpIOException(rejectException);
            }
            LOG.error(
                    "业务消息消费失败，已送入 Consumer DLQ | consumerCode={} messageId={} eventCode={}",
                    consumer.consumerCode(),
                    messageId,
                    consumer.eventType().code(),
                    exception);
            return;
        }

        // Consumer 正常返回后才确认；确认结果不确定时关闭 Channel，由 RabbitMQ 决定是否重新投递。
        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException exception) {
            throw new AmqpIOException(
                    new IOException(
                            "业务消息 ACK 失败 | consumerCode=" + consumer.consumerCode() + " messageId=" + messageId,
                            exception));
        }
        LOG.trace(
                "业务消息消费完成 | consumerCode={} messageId={} eventCode={}",
                consumer.consumerCode(),
                messageId,
                consumer.eventType().code());
    }
}
