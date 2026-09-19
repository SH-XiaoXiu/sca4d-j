package com.sca4d.messaging.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.messaging.MessageConsumer;
import com.sca4d.core.api.messaging.MessagePublisher;
import com.sca4d.core.api.runtime.RuntimeNamespace;
import com.sca4d.core.api.scheduling.ClusterTaskSchedule;
import com.sca4d.core.api.scheduling.IntervalSchedule;
import com.sca4d.core.api.scheduling.MisfirePolicy;
import com.sca4d.core.api.scheduling.ScheduleKey;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.messaging.outbox.OutboxCleanupTask;
import com.sca4d.messaging.outbox.OutboxDispatchTask;
import com.sca4d.messaging.outbox.OutboxDispatcher;
import com.sca4d.messaging.outbox.OutboxRepository;
import com.sca4d.messaging.outbox.mapper.OutboxMapper;
import com.sca4d.messaging.rabbit.ConsumerContainers;
import com.sca4d.messaging.rabbit.MessagingEnvelopeCodec;
import com.sca4d.messaging.rabbit.MessagingTopology;
import com.sca4d.messaging.rabbit.RabbitMessagePublisher;
import com.sca4d.messaging.rabbit.RabbitMessageSender;
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
    MessagingTopology messagingTopology(@NotNull RuntimeNamespace runtimeNamespace) {
        return new MessagingTopology(runtimeNamespace);
    }

    /** @return 业务 exchange、Consumer 队列和各自 DLQ 的持久声明 */
    @Bean
    Declarables messagingDeclarables(
            @NotNull MessagingTopology topology,
            @NotNull List<@NotNull MessageConsumer<?>> consumers) {
        return topology.declarables(consumers);
    }

    /** @return 与应用 JSON 配置隔离的业务消息信封编解码器 */
    @Bean
    MessagingEnvelopeCodec messagingEnvelopeCodec(@Sca4djJson @NotNull ObjectMapper objectMapper) {
        return new MessagingEnvelopeCodec(objectMapper);
    }

    /** @return Outbox 持久化语义 */
    @Bean
    OutboxRepository outboxRepository(@NotNull OutboxMapper mapper) {
        return new OutboxRepository(mapper);
    }

    /**
     * 创建强制 mandatory return 和 correlated confirm 的 RabbitMQ 发布操作。
     *
     * @throws IllegalStateException ConnectionFactory 不是 Spring Boot 默认缓存实现时抛出
     */
    @Bean
    RabbitMessageSender rabbitMessageSender(
            @NotNull ConnectionFactory connectionFactory,
            @NotNull MessagingTopology topology,
            @NotNull MessagingProperties properties) {
        if (!(connectionFactory instanceof CachingConnectionFactory caching)) {
            throw new IllegalStateException("Messaging 要求使用 CachingConnectionFactory 支持发布确认");
        }
        caching.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        caching.setPublisherReturns(true);
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        return new RabbitMessageSender(rabbitTemplate, topology, properties);
    }

    /** @return 支持立即、提交后和 Outbox 发布的公共 MessagePublisher */
    @Bean
    MessagePublisher messagePublisher(
            @NotNull IdGenerator ids,
            @NotNull MessagingEnvelopeCodec codec,
            @NotNull RabbitMessageSender sender,
            @NotNull OutboxRepository outbox,
            @NotNull Clock clock,
            @NotNull RuntimeNamespace runtimeNamespace) {
        return new RabbitMessagePublisher(ids, codec, sender, outbox, clock, runtimeNamespace);
    }

    /** @return 每个 MessageConsumer 对应的竞争消费容器 */
    @Bean
    ConsumerContainers messagingConsumerContainers(
            @NotNull ConnectionFactory connectionFactory,
            @NotNull MessagingTopology topology,
            @NotNull MessagingEnvelopeCodec codec,
            @NotNull List<@NotNull MessageConsumer<?>> consumers) {
        return new ConsumerContainers(connectionFactory, topology, codec, consumers);
    }

    /** @return 负责租约领取、发布结果写回和 SENT 清理的 Outbox 任务 */
    @Bean
    OutboxDispatcher outboxDispatcher(
            @NotNull OutboxRepository repository,
            @NotNull RabbitMessageSender sender,
            @NotNull PlatformTransactionManager transactionManager,
            @NotNull MessagingProperties properties,
            @NotNull Clock clock) {
        return new OutboxDispatcher(
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
     * @return 使用 OutboxDispatcher 逐条派发到期消息的集群任务
     */
    @Bean
    OutboxDispatchTask outboxDispatchTask(@NotNull OutboxDispatcher dispatcher) {
        return new OutboxDispatchTask(dispatcher);
    }

    /**
     * 创建执行一次过期 SENT 记录清理的基础设施任务。
     *
     * @param dispatcher 按保留期分批删除 SENT 记录的 Outbox 派发器
     * @return 使用 OutboxDispatcher 分批删除过期 SENT 记录的集群任务
     */
    @Bean
    OutboxCleanupTask outboxCleanupTask(@NotNull OutboxDispatcher dispatcher) {
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
                new ScheduleKey("messaging.outbox.dispatch"),
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
                new ScheduleKey("messaging.outbox.cleanup"),
                1,
                task,
                new IntervalSchedule(properties.getCleanupInterval(), properties.getCleanupInterval()),
                MisfirePolicy.FIRE_ONCE_NOW);
    }
}
