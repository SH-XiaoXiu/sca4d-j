package com.sca4d.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.common.messaging.DefaultMessagePublisher;
import com.sca4d.common.messaging.MessagePublisher;
import com.sca4d.common.messaging.outbox.DefaultMessageOutboxDeliveryService;
import com.sca4d.common.messaging.outbox.MessageOutboxDeliveryService;
import com.sca4d.common.messaging.outbox.MessageOutboxDispatcher;
import com.sca4d.common.messaging.outbox.MessageOutboxRepository;
import com.sca4d.common.messaging.rabbit.RabbitMessageSender;
import com.sca4d.common.messaging.rabbit.RabbitMessagingTopology;
import com.sca4d.config.properties.Sca4djMessagingProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 消息发布配置。
 */
@Configuration
@EnableConfigurationProperties(Sca4djMessagingProperties.class)
@ConditionalOnProperty(prefix = "sca4dj.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MessagingConfig {

    /**
     * 注册 RabbitMQ 全局 JSON 消息转换器。
     *
     * @param objectMapper JSON 序列化器
     * @return RabbitMQ 全局 JSON 消息转换器
     */
    @Bean("rabbitMessageConverter")
    @ConditionalOnMissingBean(MessageConverter.class)
    public @NotNull Jackson2JsonMessageConverter rabbitMessageConverter(@NotNull ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * 注册 RabbitMQ 消息发送器。
     *
     * @param rabbitTemplate RabbitMQ 模板
     * @param properties 消息发布配置
     * @return RabbitMQ 消息发送器
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull RabbitMessageSender rabbitMessageSender(
            @NotNull RabbitTemplate rabbitTemplate, @NotNull Sca4djMessagingProperties properties) {
        return new RabbitMessageSender(rabbitTemplate, properties);
    }

    /**
     * 注册共用消息发布器。
     *
     * @param sender RabbitMQ 消息发送器
     * @param outboxRepository Outbox 仓储提供器
     * @param objectMapper JSON 序列化器
     * @param idGenerator ID 生成器
     * @param properties 消息发布配置
     * @return 共用消息发布器
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull MessagePublisher messagePublisher(
            @NotNull RabbitMessageSender sender,
            @NotNull ObjectProvider<MessageOutboxRepository> outboxRepository,
            @NotNull ObjectMapper objectMapper,
            @NotNull IdGenerator idGenerator,
            @NotNull Sca4djMessagingProperties properties) {
        return new DefaultMessagePublisher(sender, outboxRepository, objectMapper, idGenerator, properties);
    }

    /**
     * 注册 RabbitMQ 默认消息拓扑声明器。
     *
     * @param amqpAdmin AMQP 管理器
     * @param properties 消息发布配置
     * @return RabbitMQ 默认消息拓扑声明器
    */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull RabbitMessagingTopology rabbitMessagingTopology(
            @NotNull AmqpAdmin amqpAdmin, @NotNull Sca4djMessagingProperties properties) {
        return new RabbitMessagingTopology(amqpAdmin, properties);
    }

    /**
     * 创建领取 Outbox 消息、发送到 RabbitMQ 并更新租约状态的服务。
     *
     * @param repository Outbox 仓储
     * @param sender RabbitMQ 消息发送器
     * @param objectMapper JSON 序列化器
     * @param properties 消息发布配置
     * @return 到期 Outbox 消息的发送服务
     */
    @Bean
    @ConditionalOnBean({MessageOutboxRepository.class, RabbitMessageSender.class})
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "sca4dj.messaging.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
    public @NotNull MessageOutboxDeliveryService messageOutboxDeliveryService(
            @NotNull MessageOutboxRepository repository,
            @NotNull RabbitMessageSender sender,
            @NotNull ObjectMapper objectMapper,
            @NotNull Sca4djMessagingProperties properties) {
        return new DefaultMessageOutboxDeliveryService(repository, sender, objectMapper, properties);
    }

    /**
     * 提供 Outbox 投递的当前调度实现。
     *
     * @param deliveryService 领取并发送到期消息的服务
     * @return 基于当前调度公共组件的 Outbox 触发器
     */
    @Bean
    @ConditionalOnBean(MessageOutboxDeliveryService.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "sca4dj.messaging.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
    public @NotNull MessageOutboxDispatcher messageOutboxDispatcher(
            @NotNull MessageOutboxDeliveryService deliveryService) {
        return new MessageOutboxDispatcher(deliveryService);
    }
}
