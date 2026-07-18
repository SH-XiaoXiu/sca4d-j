package com.sca4d.common.messaging.rabbit;

import com.sca4d.config.properties.Sca4djMessagingProperties;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * RabbitMQ 默认消息拓扑声明器。
 */
public class RabbitMessagingTopology {

    private static final Logger log = LoggerFactory.getLogger(RabbitMessagingTopology.class);

    private final AmqpAdmin amqpAdmin;
    private final Sca4djMessagingProperties properties;

    /**
     * 创建 RabbitMQ 默认消息拓扑声明器。
     *
     * @param amqpAdmin AMQP 管理器
     * @param properties 消息发布配置
     */
    public RabbitMessagingTopology(
            @NotNull AmqpAdmin amqpAdmin, @NotNull Sca4djMessagingProperties properties) {
        this.amqpAdmin = amqpAdmin;
        this.properties = properties;
    }

    /**
     * 应用启动后声明默认 exchange、queue、DLX 和 DLQ。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void declare() {
        if (!properties.getRabbit().isDeclareDefaultTopology()) {
            log.debug("RabbitMQ 默认消息拓扑未启用");
            return;
        }
        long startedAt = System.nanoTime();
        Sca4djMessagingProperties.Rabbit rabbit = properties.getRabbit();
        log.info(
                "RabbitMQ 默认消息拓扑声明开始 | defaultExchange={} defaultQueue={} deadLetterExchange={} deadLetterQueue={}",
                rabbit.getDefaultExchange(),
                rabbit.getDefaultQueue(),
                rabbit.getDeadLetterExchange(),
                rabbit.getDeadLetterQueue());
        TopicExchange deadLetterExchange = ExchangeBuilder.topicExchange(rabbit.getDeadLetterExchange()).durable(true).build();
        amqpAdmin.declareExchange(deadLetterExchange);
        Queue deadLetterQueue = QueueBuilder.durable(rabbit.getDeadLetterQueue()).build();
        amqpAdmin.declareQueue(deadLetterQueue);
        amqpAdmin.declareBinding(BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(rabbit.getDeadLetterRoutingKey()));

        TopicExchange defaultExchange = ExchangeBuilder.topicExchange(rabbit.getDefaultExchange()).durable(true).build();
        amqpAdmin.declareExchange(defaultExchange);
        Queue defaultQueue = QueueBuilder.durable(rabbit.getDefaultQueue())
                .deadLetterExchange(rabbit.getDeadLetterExchange())
                .deadLetterRoutingKey(rabbit.getDeadLetterRoutingKey())
                .build();
        amqpAdmin.declareQueue(defaultQueue);
        amqpAdmin.declareBinding(BindingBuilder.bind(defaultQueue)
                .to(defaultExchange)
                .with(rabbit.getDefaultRoutingKey()));
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info(
                "RabbitMQ 默认消息拓扑声明完成 | exchangeCount={} queueCount={} bindingCount={} durationMs={}",
                2,
                2,
                2,
                durationMs);
    }
}
