package com.sca4d.messaging.rabbit;

import com.sca4d.core.api.messaging.MessageConsumer;
import com.sca4d.core.api.runtime.RuntimeNamespace;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** 校验 MessageConsumer 契约，并声明持久业务 exchange、Consumer 队列和独立 DLQ。 */
@ApiStatus.Internal
public final class MessagingTopology {

    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    private final @NotNull String exchangeName;

    private final @NotNull String deadLetterExchangeName;

    private final @NotNull RuntimeNamespace runtimeNamespace;

    /** 创建当前 App 的 Messaging exchange 命名。 */
    public MessagingTopology(@NotNull RuntimeNamespace runtimeNamespace) {
        RuntimeNamespace namespace = Objects.requireNonNull(runtimeNamespace, "运行时命名空间不能为空");
        this.runtimeNamespace = namespace;
        this.exchangeName = namespace.sca4dj("messaging", "event");
        this.deadLetterExchangeName = namespace.sca4dj("messaging", "dead-letter");
    }

    /** @return 业务消息持久 topic exchange 名称 */
    @NotNull
    public String exchangeName() {
        return exchangeName;
    }

    /** @return 指定 Consumer 的持久队列名称 */
    @NotNull
    String queueName(@NotNull MessageConsumer<?> consumer) {
        return runtimeNamespace.sca4dj("messaging", "consumer", consumer.consumerCode());
    }

    /** @return 指定 Consumer 的持久死信队列名称 */
    @NotNull
    String deadLetterQueueName(@NotNull MessageConsumer<?> consumer) {
        return runtimeNamespace.sca4dj("messaging", "consumer", consumer.consumerCode(), "dead-letter");
    }

    /**
     * 为已经安装的 Consumer 创建 Spring AMQP 持久对象和绑定。
     *
     * @param consumers 当前 App 安装的全部业务消息 Consumer
     * @return Spring AMQP 启动时需要声明的全部持久对象和绑定
     */
    @NotNull
    public Declarables declarables(@NotNull List<@NotNull MessageConsumer<?>> consumers) {
        List<MessageConsumer<?>> checkedConsumers = validatedConsumers(consumers);
        TopicExchange exchange = new TopicExchange(exchangeName, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(deadLetterExchangeName, true, false);
        List<Declarable> result = new ArrayList<>();
        result.add(exchange);
        result.add(deadLetterExchange);

        for (MessageConsumer<?> consumer : checkedConsumers) {
            String queueName = queueName(consumer);
            String deadLetterQueueName = deadLetterQueueName(consumer);
            Queue queue = QueueBuilder.durable(queueName)
                    .deadLetterExchange(deadLetterExchangeName)
                    .deadLetterRoutingKey(queueName)
                    .build();
            Queue deadLetterQueue = QueueBuilder.durable(deadLetterQueueName).build();
            Binding binding = BindingBuilder.bind(queue).to(exchange).with(consumer.eventType().code());
            Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue)
                    .to(deadLetterExchange)
                    .with(queueName);
            result.add(queue);
            result.add(deadLetterQueue);
            result.add(binding);
            result.add(deadLetterBinding);
        }
        return new Declarables(result);
    }

    /**
     * 复制并校验当前 App 安装的全部 Consumer。
     *
     * @param consumers 待校验的 Consumer
     * @return 保持声明顺序的不可修改列表
     */
    @NotNull
    List<@NotNull MessageConsumer<?>> validatedConsumers(
            @NotNull List<@NotNull MessageConsumer<?>> consumers) {
        List<MessageConsumer<?>> checkedConsumers = List.copyOf(
                Objects.requireNonNull(consumers, "MessageConsumer 列表不能为空"));
        Set<String> codes = new HashSet<>();
        for (MessageConsumer<?> consumer : checkedConsumers) {
            validate(consumer);
            if (!codes.add(consumer.consumerCode())) {
                throw new IllegalStateException("MessageConsumer 编码重复: " + consumer.consumerCode());
            }
        }
        return checkedConsumers;
    }

    /** 校验 Consumer 编码、消息编码、版本和 payload 类型。 */
    private static void validate(MessageConsumer<?> consumer) {
        MessageConsumer<?> checked = Objects.requireNonNull(consumer, "MessageConsumer 不能为空");
        if (!CODE.matcher(Objects.requireNonNull(checked.consumerCode(), "Consumer 编码不能为空")).matches()) {
            throw new IllegalStateException("Consumer 编码只能包含字母、数字、点、下划线和连字符");
        }
        String eventCode = Objects.requireNonNull(checked.eventType(), "EventType 不能为空").code();
        if (!CODE.matcher(eventCode).matches()) {
            throw new IllegalStateException("事件编码只能包含字母、数字、点、下划线和连字符");
        }
        Objects.requireNonNull(checked.eventType().payloadType(), "Consumer payload 类型不能为空");
    }
}
