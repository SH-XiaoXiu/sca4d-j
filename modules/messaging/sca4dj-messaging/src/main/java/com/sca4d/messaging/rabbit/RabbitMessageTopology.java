package com.sca4d.messaging.rabbit;

import com.sca4d.core.api.messaging.EventHandler;
import com.sca4d.core.api.messaging.MessageHandler;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 校验 Message Handler 契约，并声明持久业务 exchange、Handler 队列、retry queue 和 DLQ。 */
@ApiStatus.Internal
public final class RabbitMessageTopology {

    private final @NotNull String exchangeName;

    private final @NotNull String retryExchangeName;

    private final @NotNull String deadLetterExchangeName;

    private final @NotNull RuntimeNamespace runtimeNamespace;

    /** 创建当前对象。 */
    public RabbitMessageTopology(@NotNull RuntimeNamespace runtimeNamespace) {
        RuntimeNamespace namespace = Objects.requireNonNull(runtimeNamespace, "运行时命名空间不能为空");
        this.runtimeNamespace = namespace;
        this.exchangeName = namespace.sca4dj("messaging", "message");
        this.retryExchangeName = namespace.sca4dj("messaging", "retry");
        this.deadLetterExchangeName = namespace.sca4dj("messaging", "dead-letter");
    }

    /** 执行 exchangeName 方法表示的业务操作。 */
    public @NotNull String exchangeName() {
        return exchangeName;
    }

    @NotNull
    String retryExchangeName() {
        return retryExchangeName;
    }

    @NotNull
    String queueName(@NotNull RabbitHandlerDescriptor handler) {
        return runtimeNamespace.sca4dj("messaging", "handler", handler.code().value());
    }

    @NotNull
    String retryQueueName(@NotNull RabbitHandlerDescriptor handler) {
        return runtimeNamespace.sca4dj("messaging", "handler", handler.code().value(), "retry");
    }

    @NotNull
    String deadLetterQueueName(@NotNull RabbitHandlerDescriptor handler) {
        return runtimeNamespace.sca4dj("messaging", "handler", handler.code().value(), "dead-letter");
    }

    /** 执行 declarables 方法表示的业务操作。 */
    public @NotNull Declarables declarables(
            @NotNull List<? extends @NotNull EventHandler<?>> eventHandlers,
            @NotNull List<? extends @NotNull MessageHandler<?>> messageHandlers) {
        List<RabbitHandlerDescriptor> handlers = descriptors(eventHandlers, messageHandlers);
        DirectExchange exchange = new DirectExchange(exchangeName, true, false);
        DirectExchange retryExchange = new DirectExchange(retryExchangeName, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(deadLetterExchangeName, true, false);
        List<Declarable> result = new ArrayList<>();
        result.add(exchange);
        result.add(retryExchange);
        result.add(deadLetterExchange);

        for (RabbitHandlerDescriptor handler : handlers) {
            String queueName = queueName(handler);
            String deadLetterQueueName = deadLetterQueueName(handler);
            Queue queue = QueueBuilder.durable(queueName)
                    .deadLetterExchange(deadLetterExchangeName)
                    .deadLetterRoutingKey(queueName)
                    .build();
            Queue deadLetterQueue = QueueBuilder.durable(deadLetterQueueName).build();
            Binding binding = BindingBuilder.bind(queue).to(exchange).with(handler.messageCode().value());
            Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue)
                    .to(deadLetterExchange)
                    .with(queueName);
            result.add(queue);
            result.add(deadLetterQueue);
            result.add(binding);
            result.add(deadLetterBinding);

            if (handler.handlingPolicy().maximumAttempts() > 1) {
                long retryDelay = Math.max(1L, handler.handlingPolicy().retryDelay().toMillis());
                String retryQueueName = retryQueueName(handler);
                Queue retryQueue = QueueBuilder.durable(retryQueueName)
                        .ttl((int) Math.min(Integer.MAX_VALUE, retryDelay))
                        .deadLetterExchange("")
                        .deadLetterRoutingKey(queueName)
                        .build();
                Binding retryBinding = BindingBuilder.bind(retryQueue)
                        .to(retryExchange)
                        .with(handler.code().value());
                result.add(retryQueue);
                result.add(retryBinding);
            }
        }
        return new Declarables(result);
    }

    @NotNull
    List<@NotNull RabbitHandlerDescriptor> descriptors(
            @NotNull List<? extends @NotNull EventHandler<?>> eventHandlers,
            @NotNull List<? extends @NotNull MessageHandler<?>> messageHandlers) {
        List<RabbitHandlerDescriptor> result = new ArrayList<>();
        Objects.requireNonNull(eventHandlers, "EventHandler 列表不能为空")
                .forEach(handler -> result.add(RabbitHandlerDescriptor.event(handler)));
        Objects.requireNonNull(messageHandlers, "MessageHandler 列表不能为空")
                .forEach(handler -> result.add(RabbitHandlerDescriptor.message(handler)));
        Set<String> codes = new HashSet<>();
        for (RabbitHandlerDescriptor handler : result) {
            if (!codes.add(handler.code().value())) {
                throw new IllegalStateException("MessageHandler 编码重复: " + handler.code());
            }
        }
        return List.copyOf(result);
    }
}
