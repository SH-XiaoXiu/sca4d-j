# 发布业务消息、实时更新和通知

以下示例在订单创建成功后执行三项操作：向 RabbitMQ 发布 `order.created`，通知在线租户刷新订单列表，并为指定 Principal 创建通知。

## 前置条件

- RabbitMQ 已启动。
- `SCA4DJ_MESSAGING_ENABLED=true`。
- 需要 SSE 或 WebSocket 时设置 `SCA4DJ_REALTIME_ENABLED=true`。
- Service 写方法由 `@Transactional` 管理。

## 1. 定义消息内容

消息只包含消费者需要的字段，不直接序列化 Entity、Request 或 View：

```java
public final class OrderCreatedEvent {
    private final Long orderId;
    private final Long tenantId;
    private final Long createdByPrincipalId;
    private final OffsetDateTime createdAt;

    public OrderCreatedEvent(
            Long orderId,
            Long tenantId,
            Long createdByPrincipalId,
            OffsetDateTime createdAt) {
        this.orderId = orderId;
        this.tenantId = tenantId;
        this.createdByPrincipalId = createdByPrincipalId;
        this.createdAt = createdAt;
    }

    public Long getOrderId() { return orderId; }
    public Long getTenantId() { return tenantId; }
    public Long getCreatedByPrincipalId() { return createdByPrincipalId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
```

事件名、exchange 和 routing key 使用业务模块常量：

```java
public final class OrderEvents {

    public static final String EXCHANGE = "sca4dj.event";
    public static final String CREATED = "order.created";
    public static final String CREATED_ROUTING_KEY = "order.created";

    private OrderEvents() {
    }
}
```

## 2. 选择发布模式

| 模式 | 使用情况 |
| --- | --- |
| `DIRECT` | 消息与当前数据库事务无关 |
| `AFTER_COMMIT` | 只用于页面刷新等可以重新查询的数据 |
| `OUTBOX` | 业务消息不能因应用在提交后退出而丢失 |

订单创建消息使用 OUTBOX：

```java
MessageCommand command = MessageCommand.of(
        OrderEvents.EXCHANGE,
        OrderEvents.CREATED_ROUTING_KEY,
        OrderEvents.CREATED,
        new OrderCreatedEvent(
                order.getOrderId(),
                order.getTenantId(),
                actor.requirePrincipalId(),
                order.getCreatedAt()))
        .setBusinessKey(order.getOrderId().toString());

messagePublisher.publishReliable(command);
```

`publishReliable` 在当前事务中写入 `sys_message_outbox`。事务回滚时订单和 Outbox 记录一起回滚。`MessageOutboxDispatcher` 在后台领取并发送记录。

页面刷新消息可以使用：

```java
messagePublisher.publishAfterCommit(
        OrderEvents.EXCHANGE,
        "order.list.changed",
        "order.list.changed",
        Map.of("tenantId", order.getTenantId()));
```

## 3. 发布实时更新

向租户内所有在线连接发送订单列表变化：

```java
realtimePublisher.sendToTenant(
        order.getTenantId(),
        "order.list.changed",
        Map.of("orderId", order.getOrderId().toString()));
```

`RealtimePublisher` 在事务存在时等事务提交后发送。RabbitMQ 将消息广播到各应用实例，每个实例只向本机匹配的 SSE 和 STOMP 连接发送。

其他目标：

```java
realtimePublisher.sendToSubject(
        subjectId, "profile.changed", Map.of());

realtimePublisher.sendToPrincipal(
        PrincipalType.PLATFORM,
        principalId,
        "platform.dashboard.changed",
        Map.of());

realtimePublisher.sendToTopic(
        "system.announcements",
        "announcement.published",
        Map.of("announcementId", announcementId.toString()));
```

客户端收到事件后重新请求订单列表。事件中的 data 不替代数据库查询结果。

## 4. 创建通知

```java
Long notificationId = notificationService.create(
        "订单已创建",
        "订单 " + order.getOrderNo() + " 已创建",
        NotificationCategory.SYSTEM,
        actor.getPrincipalType(),
        actor.requirePrincipalId(),
        Map.of("orderId", order.getOrderId().toString()),
        List.of(NotificationRecipient.principal(
                PrincipalType.TENANT,
                recipientPrincipalId)));
```

标题最长 200 字符，正文最长 2000 字符。接收人可以使用 Subject、Principal，或同时指定 Subject 和 Principal。

通知创建后会更新接收人的未读数量，并通过实时通信发送刷新事件。客户端仍可通过以下接口查询：

```text
GET  /notifications
GET  /notifications/unread-count
PUT /notifications/{notificationId}/read-status
PUT /notifications/read-status
```

## 5. 消费 RabbitMQ 消息

先声明队列和 binding：

```java
@Bean
public Queue orderCreatedQueue() {
    return QueueBuilder.durable("order.created.consumer")
            .withArgument("x-dead-letter-exchange", "sca4dj.event.dlx")
            .withArgument("x-dead-letter-routing-key", "dead")
            .build();
}

@Bean
public Binding orderCreatedBinding(Queue orderCreatedQueue) {
    TopicExchange exchange = new TopicExchange(
            OrderEvents.EXCHANGE, true, false);
    return BindingBuilder.bind(orderCreatedQueue)
            .to(exchange)
            .with(OrderEvents.CREATED_ROUTING_KEY);
}
```

消费者按 `messageId` 或业务唯一键处理重复投递。以下 Listener 使用订单 ID 的唯一约束防止重复创建下游记录：

```java
@RabbitListener(queues = "order.created.consumer")
public void onOrderCreated(MessageEnvelope<OrderCreatedEvent> envelope) {
    OrderCreatedEvent event = envelope.getPayload();
    orderProjectionRepository.createIfAbsent(
            event.getOrderId(),
            event.getTenantId(),
            event.getCreatedAt());
}
```

队列、exchange 和 binding 应由消息配置或部署脚本声明。消费者抛出异常时由 RabbitMQ 重试或进入死信队列，不要捕获异常后直接返回成功。

## 6. 运行结果

订单创建请求成功后检查：

1. 订单表存在新记录；
2. `sys_message_outbox` 存在 `order.created`，随后状态变为 `SENT`；
3. RabbitMQ 消费者收到包含同一 `messageId` 的 envelope；
4. 连接在另一应用实例时仍能收到实时更新；
5. 通知列表出现新通知，未读数量增加；
6. 重复投递同一消息不会创建重复下游记录。

## 失败处理

RabbitMQ 停止时，订单事务和 Outbox 记录仍可提交；恢复 RabbitMQ 后 Dispatcher 继续发送。需要验证重试次数和 DEAD 状态时，调整 Outbox 测试配置，不要在生产数据库手工修改状态。

## 下一步

消息需要在管理端展示时，继续[添加管理端页面](./frontend.md)。
