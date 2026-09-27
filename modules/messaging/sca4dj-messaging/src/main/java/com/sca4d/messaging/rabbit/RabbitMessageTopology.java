package com.sca4d.messaging.rabbit;

import static com.sca4d.core.api.name.NameFormat.COLON;

import com.sca4d.core.api.messaging.EventHandler;
import com.sca4d.core.api.messaging.MessageHandler;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
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

    private final @NotNull Key exchangeName;

    private final @NotNull Key retryExchangeName;

    private final @NotNull Key deadLetterExchangeName;

    /** 使用固定业务消息、重试和死信 exchange 名称建立拓扑定义。 */
    public RabbitMessageTopology() {
        this.exchangeName = Key.of(TreeCode.ofColon("MESSAGING:MESSAGE"));
        this.retryExchangeName = Key.of(TreeCode.ofColon("MESSAGING:RETRY"));
        this.deadLetterExchangeName = Key.of(TreeCode.ofColon("MESSAGING:DEAD_LETTER"));
    }

    /** @return 持久业务消息 exchange 名称 */
    public @NotNull Key exchangeName() {
        return exchangeName;
    }

    /** 在实际 RabbitMQ 路由边界追加部署前缀，业务消息编码保持原值。 */
    /** @param messageCode 业务消息编码 @return 业务消息路由键 */
    public @NotNull Key routingKey(@NotNull TreeCode messageCode) {
        return Key.of(TreeCode.ofColon("MESSAGING:ROUTE").append(messageCode));
    }

    /** 返回 Handler 重试路由使用的隔离名称。 */
    /** @param handlerCode Handler 编码 @return Handler 重试路由键 */
    public @NotNull Key retryRoutingKey(@NotNull TreeCode handlerCode) {
        return Key.of(TreeCode.ofColon("MESSAGING:RETRY_ROUTE").append(handlerCode));
    }

    @NotNull
    Key retryExchangeName() {
        return retryExchangeName;
    }

    @NotNull
    Key queueName(@NotNull RabbitHandlerDescriptor handler) {
        return Key.of(TreeCode.ofColon("MESSAGING:HANDLER").append(handler.code()));
    }

    @NotNull
    Key retryQueueName(@NotNull RabbitHandlerDescriptor handler) {
        return Key.of(TreeCode.ofColon("MESSAGING:HANDLER").append(handler.code()).append(TreeCode.ofSegment("RETRY")));
    }

    @NotNull
    Key deadLetterQueueName(@NotNull RabbitHandlerDescriptor handler) {
        return Key.of(
                TreeCode.ofColon("MESSAGING:HANDLER").append(handler.code()).append(TreeCode.ofSegment("DEAD_LETTER")));
    }

    /**
     * 根据全部 Handler 声明 exchange、队列、重试队列和死信队列。
     *
     * @param eventHandlers 已安装的 EventHandler
     * @param messageHandlers 已安装的 MessageHandler
     * @return RabbitMQ 声明对象集合
     */
    public @NotNull Declarables declarables(
            @NotNull List<? extends @NotNull EventHandler<?>> eventHandlers,
            @NotNull List<? extends @NotNull MessageHandler<?>> messageHandlers) {
        List<RabbitHandlerDescriptor> handlers = descriptors(eventHandlers, messageHandlers);
        DirectExchange exchange = new DirectExchange(exchangeName.format(COLON), true, false);
        DirectExchange retryExchange = new DirectExchange(retryExchangeName.format(COLON), true, false);
        DirectExchange deadLetterExchange = new DirectExchange(deadLetterExchangeName.format(COLON), true, false);
        List<Declarable> result = new ArrayList<>();
        result.add(exchange);
        result.add(retryExchange);
        result.add(deadLetterExchange);

        for (RabbitHandlerDescriptor handler : handlers) {
            Key queueName = queueName(handler);
            Key deadLetterQueueName = deadLetterQueueName(handler);
            Queue queue = QueueBuilder.durable(queueName.format(COLON))
                    .deadLetterExchange(deadLetterExchangeName.format(COLON))
                    .deadLetterRoutingKey(queueName.format(COLON))
                    .build();
            Queue deadLetterQueue = QueueBuilder.durable(deadLetterQueueName.format(COLON)).build();
            Binding binding = BindingBuilder.bind(queue)
                    .to(exchange)
                    .with(routingKey(handler.messageCode()).format(COLON));
            Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue)
                    .to(deadLetterExchange)
                    .with(queueName.format(COLON));
            result.add(queue);
            result.add(deadLetterQueue);
            result.add(binding);
            result.add(deadLetterBinding);

            if (handler.handlingPolicy().maximumAttempts() > 1) {
                long retryDelay = Math.max(1L, handler.handlingPolicy().retryDelay().toMillis());
                Key retryQueueName = retryQueueName(handler);
                Queue retryQueue = QueueBuilder.durable(retryQueueName.format(COLON))
                        .ttl((int) Math.min(Integer.MAX_VALUE, retryDelay))
                        .deadLetterExchange("")
                        .deadLetterRoutingKey(queueName.format(COLON))
                        .build();
                Binding retryBinding = BindingBuilder.bind(retryQueue)
                        .to(retryExchange)
                        .with(retryRoutingKey(handler.code()).format(COLON));
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
        Set<TreeCode> codes = new HashSet<>();
        for (RabbitHandlerDescriptor handler : result) {
            if (!codes.add(handler.code())) {
                throw new IllegalStateException("MessageHandler 编码重复: " + handler.code());
            }
        }
        return List.copyOf(result);
    }
}
