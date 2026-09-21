package com.sca4d.messaging.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.messaging.EventHandler;
import com.sca4d.core.api.messaging.MessageBus;
import com.sca4d.core.api.messaging.MessageHandler;
import com.sca4d.core.api.runtime.RuntimeNamespace;
import com.sca4d.core.api.scheduling.ClusterTaskSchedule;
import com.sca4d.core.api.scheduling.IntervalSchedule;
import com.sca4d.core.api.scheduling.MisfirePolicy;
import com.sca4d.core.api.scheduling.ScheduleKey;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.messaging.outbox.MessageOutboxDispatcher;
import com.sca4d.messaging.outbox.MessageOutboxRepository;
import com.sca4d.messaging.outbox.OutboxCleanupTask;
import com.sca4d.messaging.outbox.OutboxDispatchTask;
import com.sca4d.messaging.outbox.mapper.OutboxMapper;
import com.sca4d.messaging.rabbit.RabbitHandlerContainers;
import com.sca4d.messaging.rabbit.RabbitMessageBus;
import com.sca4d.messaging.rabbit.RabbitMessageEnvelopeCodec;
import com.sca4d.messaging.rabbit.RabbitMessageTopology;
import com.sca4d.messaging.rabbit.RabbitPublisher;
import org.jetbrains.annotations.NotNull;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;

/** 自动装配业务消息 RabbitMQ 拓扑、Consumer 容器、发布器和 Outbox 派发。 */
@AutoConfiguration(after = RabbitAutoConfiguration.class)
@MapperScan(basePackageClasses = OutboxMapper.class)
@EnableConfigurationProperties(MessagingProperties.class)
@EnableTransactionManagement
public class MessagingAutoConfiguration {

    /** 创建 Messaging 模块自动配置。 */
    public MessagingAutoConfiguration() {
    }

    /** @return 确定当前应用 RabbitMQ exchange 和 Consumer 队列名称的拓扑 */
    @Bean
    RabbitMessageTopology messagingTopology(@NotNull RuntimeNamespace runtimeNamespace) {
        return new RabbitMessageTopology(runtimeNamespace);
    }

    /** @return 业务 exchange、Consumer 队列和各自 DLQ 的持久声明 */
    @Bean
    Declarables messagingDeclarables(
            @NotNull RabbitMessageTopology topology,
            @NotNull List<@NotNull EventHandler<?>> eventHandlers,
            @NotNull List<@NotNull MessageHandler<?>> messageHandlers) {
        return topology.declarables(eventHandlers, messageHandlers);
    }

    /** @return 与应用 JSON 配置隔离的业务消息信封编解码器 */
    @Bean
    RabbitMessageEnvelopeCodec messagingEnvelopeCodec(
            @Sca4djJson @NotNull ObjectMapper objectMapper,
            @NotNull List<@NotNull MessageHandler<?>> messageHandlers) {
        return new RabbitMessageEnvelopeCodec(objectMapper, messageHandlers);
    }

    /** @return Outbox 持久化语义 */
    @Bean
    MessageOutboxRepository outboxRepository(@NotNull OutboxMapper mapper) {
        return new MessageOutboxRepository(mapper);
    }

    /**
     * 创建强制 mandatory return 和 correlated confirm 的 RabbitMQ 发布操作。
     *
     * @throws IllegalStateException ConnectionFactory 不是 Spring Boot 默认缓存实现时抛出
     */
    @Bean
    RabbitPublisher rabbitMessageSender(
            @NotNull ConnectionFactory connectionFactory,
            @NotNull RabbitMessageTopology topology,
            @NotNull MessagingProperties properties) {
        if (!(connectionFactory instanceof CachingConnectionFactory caching)) {
            throw new IllegalStateException("Messaging 要求使用 CachingConnectionFactory 支持发布确认");
        }
        caching.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        caching.setPublisherReturns(true);
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        return new RabbitPublisher(rabbitTemplate, topology, properties);
    }

    /** @return 支持立即、提交后和 Outbox 发布的公共 MessageBus */
    @Bean
    MessageBus messageBus(
            @NotNull IdGenerator ids,
            @NotNull RabbitMessageEnvelopeCodec codec,
            @NotNull RabbitPublisher sender,
            @NotNull MessageOutboxRepository outbox,
            @NotNull Clock clock,
            @NotNull RuntimeNamespace runtimeNamespace) {
        return new RabbitMessageBus(ids, codec, sender, outbox, clock, runtimeNamespace);
    }

    /** @return 每个 EventHandler 和 MessageHandler 对应的竞争消费容器 */
    @Bean
    RabbitHandlerContainers messagingRabbitHandlerContainers(
            @NotNull ConnectionFactory connectionFactory,
            @NotNull RabbitMessageTopology topology,
            @NotNull RabbitMessageEnvelopeCodec codec,
            @NotNull MessagingProperties properties,
            @NotNull RabbitPublisher publisher,
            @NotNull List<@NotNull EventHandler<?>> eventHandlers,
            @NotNull List<@NotNull MessageHandler<?>> messageHandlers) {
        return new RabbitHandlerContainers(
                connectionFactory,
                topology,
                codec,
                properties,
                publisher,
                eventHandlers,
                messageHandlers);
    }

    /** @return 负责租约领取、发布结果写回和 SENT 清理的 Outbox 任务 */
    @Bean
    MessageOutboxDispatcher outboxDispatcher(
            @NotNull MessageOutboxRepository repository,
            @NotNull RabbitPublisher sender,
            @NotNull PlatformTransactionManager transactionManager,
            @NotNull MessagingProperties properties,
            @NotNull Clock clock) {
        return new MessageOutboxDispatcher(
                repository,
                sender,
                new TransactionTemplate(transactionManager),
                properties,
                clock);
    }

    /**
     * 创建执行一次到期消息派发的基础设施任务。
     *
     * @param dispatcher 保留消息级租约和退避语义的 Outbox 派发器
     * @return 使用 MessageOutboxDispatcher 逐条派发到期消息的集群任务
     */
    @Bean
    OutboxDispatchTask outboxDispatchTask(@NotNull MessageOutboxDispatcher dispatcher) {
        return new OutboxDispatchTask(dispatcher);
    }

    /**
     * 创建执行一次过期 SENT 记录清理的基础设施任务。
     *
     * @param dispatcher 按保留期分批删除 SENT 记录的 Outbox 派发器
     * @return 使用 MessageOutboxDispatcher 分批删除过期 SENT 记录的集群任务
     */
    @Bean
    OutboxCleanupTask outboxCleanupTask(@NotNull MessageOutboxDispatcher dispatcher) {
        return new OutboxCleanupTask(dispatcher);
    }

    /**
     * 创建由 Quartz 集群直接执行 OutboxDispatchTask 的代码计划。
     *
     * @param task 每次触发时执行的 Outbox 派发任务
     * @param properties 派发间隔配置
     * @return 高频执行 Outbox 派发且不创建 Job run 的集群任务计划
     */
    @Bean
    ClusterTaskSchedule outboxDispatchSchedule(
            @NotNull OutboxDispatchTask task,
            @NotNull MessagingProperties properties) {
        return new ClusterTaskSchedule(
                new ScheduleKey("MESSAGING/OUTBOX/DISPATCH"),
                1,
                task,
                new IntervalSchedule(properties.getDispatchInterval(), properties.getDispatchInterval()),
                MisfirePolicy.FIRE_ONCE_NOW);
    }

    /**
     * 创建由 Quartz 集群直接执行 OutboxCleanupTask 的代码计划。
     *
     * @param task 每次触发时执行的 Outbox 清理任务
     * @param properties 清理间隔配置
     * @return 按保留期清理间隔执行且不创建 Job run 的集群任务计划
     */
    @Bean
    ClusterTaskSchedule outboxCleanupSchedule(
            @NotNull OutboxCleanupTask task,
            @NotNull MessagingProperties properties) {
        return new ClusterTaskSchedule(
                new ScheduleKey("MESSAGING/OUTBOX/CLEANUP"),
                1,
                task,
                new IntervalSchedule(properties.getCleanupInterval(), properties.getCleanupInterval()),
                MisfirePolicy.FIRE_ONCE_NOW);
    }
}
