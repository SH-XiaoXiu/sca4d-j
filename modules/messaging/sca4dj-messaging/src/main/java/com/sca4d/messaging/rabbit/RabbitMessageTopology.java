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

/** 校验 Message Handler 契约，并声明持久业务 exchange、Handler 队列和 DLQ。 */
@ApiStatus.Internal
public class RabbitMessageTopology {

    private final @NotNull Key exchangeName;

    private final @NotNull Key deadLetterExchangeName;

    /** 使用固定业务消息和死信 exchange 名称建立拓扑定义。 */
    public RabbitMessageTopology() {
        this.exchangeName = Key.of(TreeCode.ofColon("MESSAGING:MESSAGE"));
        this.deadLetterExchangeName = Key.of(TreeCode.ofColon("MESSAGING:DEAD_LETTER"));
    }

    /** @return 持久业务消息 exchange 名称 */
    public @NotNull Key exchangeName() {
        return exchangeName;
    }

    /**
     * 在消息编码前追加 `MESSAGING:ROUTE` 路由前缀；业务消息编码本身保持原值，不做改写。
     *
     * @param messageCode 业务消息编码
     * @return 业务消息路由键
     */
    public @NotNull Key routingKey(@NotNull TreeCode messageCode) {
        return Key.of(TreeCode.ofColon("MESSAGING:ROUTE").append(messageCode));
    }

    /**
     * @param handlerCode 已注册的 Handler 编码
     * @return 使用当前应用隔离名称的目标主队列
     */
    public @NotNull Key handlerQueueName(@NotNull TreeCode handlerCode) {
        return Key.of(TreeCode.ofColon("MESSAGING:HANDLER").append(handlerCode));
    }

    @NotNull
    Key deadLetterQueueName(@NotNull RabbitHandlerDescriptor handler) {
        return Key.of(
                TreeCode.ofColon("MESSAGING:HANDLER").append(handler.code()).append(TreeCode.ofSegment("DEAD_LETTER")));
    }

    /**
     * 根据全部 Handler 声明 exchange、主队列和死信队列。
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
        DirectExchange deadLetterExchange = new DirectExchange(deadLetterExchangeName.format(COLON), true, false);
        List<Declarable> result = new ArrayList<>();
        result.add(exchange);
        result.add(deadLetterExchange);

        for (RabbitHandlerDescriptor handler : handlers) {
            Key queueName = handlerQueueName(handler.code());
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
