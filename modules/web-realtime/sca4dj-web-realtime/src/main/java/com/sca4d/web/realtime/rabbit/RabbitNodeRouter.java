package com.sca4d.web.realtime.rabbit;

import static com.sca4d.core.api.name.NameFormat.COLON;

import com.sca4d.core.api.connection.ConnectionId;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.web.realtime.config.WebRealtimeInfrastructureNames;
import com.sca4d.web.realtime.config.WebRealtimeProperties;
import com.sca4d.web.realtime.connection.BrowserWrite;
import com.sca4d.web.realtime.connection.LocalConnectionRegistry;
import com.sca4d.web.realtime.connection.NodeRouter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.context.SmartLifecycle;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 通过当前节点的临时 RabbitMQ 队列收发允许丢失的 Web Realtime 投递。 */
@ApiStatus.Internal
public final class RabbitNodeRouter implements NodeRouter, SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(RabbitNodeRouter.class);

    private final @NotNull String nodeId = UUID.randomUUID().toString();

    private final @NotNull Key exchangeName;

    private final @NotNull Key queueName;

    private final @NotNull RabbitTemplate rabbit;

    private final @NotNull NodeMessageCodec codec;

    private final @NotNull LocalConnectionRegistry localConnections;

    private final @NotNull WebRealtimeProperties properties;

    private final @NotNull SimpleMessageListenerContainer listener;

    /** 创建当前 JVM 唯一的节点路由和临时消息监听器。 */
    public RabbitNodeRouter(
            @NotNull WebRealtimeInfrastructureNames names,
            @NotNull RabbitTemplate rabbit,
            @NotNull ConnectionFactory connectionFactory,
            @NotNull NodeMessageCodec codec,
            @NotNull LocalConnectionRegistry localConnections,
            @NotNull WebRealtimeProperties properties) {
        WebRealtimeInfrastructureNames checkedNames = Objects.requireNonNull(
                names,
                "Web Realtime 基础设施名称不能为空");
        this.exchangeName = checkedNames.nodeExchange();
        this.queueName = checkedNames.nodeQueue(nodeId);
        this.rabbit = Objects.requireNonNull(rabbit, "RabbitTemplate 不能为空");
        this.codec = Objects.requireNonNull(codec, "节点消息编解码器不能为空");
        this.localConnections = Objects.requireNonNull(localConnections, "本机连接注册表不能为空");
        this.properties = Objects.requireNonNull(properties, "Web Realtime 配置不能为空");
        this.listener = new SimpleMessageListenerContainer(
                Objects.requireNonNull(connectionFactory, "RabbitMQ 连接工厂不能为空"));
        listener.setQueueNames(queueName.format(COLON));
        listener.setAcknowledgeMode(AcknowledgeMode.AUTO);
        listener.setDefaultRequeueRejected(false);
        listener.setAutoStartup(false);
        listener.setMessageListener(message -> receive(message.getBody()));
        LOG.info("Web Realtime 节点传输已启用 | mode=RABBIT nodeId={}", nodeId);
    }

    /** @return 当前节点在 Redis 和 RabbitMQ routing key 中使用的随机标识 */
    @Override
    @NotNull
    public String nodeId() {
        return nodeId;
    }

    /** @return 当前节点的非持久、独占、自动删除队列和 direct exchange 绑定 */
    @NotNull
    public Declarables declarables() {
        DirectExchange exchange = new DirectExchange(exchangeName.format(COLON), false, false);
        Queue queue = QueueBuilder.nonDurable(queueName.format(COLON))
                .exclusive()
                .autoDelete()
                .ttl((int) Math.min(Integer.MAX_VALUE, properties.getNodeMessageTtl().toMillis()))
                .build();
        Binding binding = BindingBuilder.bind(queue)
                .to(exchange)
                .with(Key.of(TreeCode.of("WEB_REALTIME:NODE:ROUTE"), nodeId).format(COLON));
        return new Declarables(exchange, queue, binding);
    }

    /** 向持有指定连接的节点发送一条非持久、短 TTL 事件。 */
    @Override
    public void send(
            @NotNull String destinationNodeId,
            @NotNull List<@NotNull ConnectionId> connectionIds,
            @NotNull BrowserWrite delivery) {
        String destination = Objects.requireNonNull(destinationNodeId, "目标节点标识不能为空");
        List<ConnectionId> connections = List.copyOf(
                Objects.requireNonNull(connectionIds, "目标连接不能为空"));
        BrowserWrite checkedDelivery = Objects.requireNonNull(delivery, "连接投递不能为空");
        if (nodeId.equals(destination)) {
            connections.forEach(connectionId -> localConnections.deliver(connectionId, checkedDelivery));
            return;
        }
        byte[] body = codec.encode(connections, checkedDelivery);
        Message rabbitMessage = MessageBuilder.withBody(body)
                .setContentType("application/json")
                .setContentEncoding("UTF-8")
                .setDeliveryMode(MessageDeliveryMode.NON_PERSISTENT)
                .setExpiration(Long.toString(properties.getNodeMessageTtl().toMillis()))
                .build();
        rabbit.send(
                exchangeName.format(COLON),
                Key.of(TreeCode.of("WEB_REALTIME:NODE:ROUTE"), destination).format(COLON),
                rabbitMessage);
    }

    /** 启动当前节点临时队列的监听。 */
    @Override
    public void start() {
        listener.start();
    }

    /** 停止当前节点临时队列的监听。 */
    @Override
    public void stop() {
        listener.stop();
    }

    /** @return RabbitMQ 监听器是否正在运行 */
    @Override
    public boolean isRunning() {
        return listener.isRunning();
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

    /** 解码节点消息并忽略已经不在本机的连接。 */
    private void receive(byte @NotNull [] body) {
        try {
            NodeMessageCodec.NodeDelivery decoded = codec.decode(body);
            decoded.connections().forEach(id -> localConnections.deliver(id, decoded.delivery()));
        } catch (IllegalArgumentException exception) {
            LOG.warn("Web Realtime 节点消息格式无效，已丢弃 | error={}", exception.getMessage());
        }
    }
}
