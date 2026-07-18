# 消息发布

业务模块通过 `MessagePublisher` 发送消息，不需要直接处理 RabbitMQ JSON 转换、confirm 或 Outbox 表。

## 发布模式

| 模式 | 发送时机 | 常见用途 |
| --- | --- | --- |
| `DIRECT` | 调用时立即发送 | 不依赖数据库事务的即时消息 |
| `AFTER_COMMIT` | 当前事务成功提交后发送 | 页面刷新、缓存失效等实时信号 |
| `OUTBOX` | 事务内写入 Outbox，再由投递器发送 | 订单创建、公告发布等业务事件 |

直接发布：

```java
messagePublisher.publishDirect(
        "sca4dj.event",
        "report.generated",
        "report.generated",
        Map.of("reportId", "report-1001"));
```

事务提交后发布：

```java
messagePublisher.publishAfterCommit(
        "sca4dj.event",
        "tenant.updated",
        "tenant.updated",
        Map.of("tenantId", "1001"));
```

可靠发布：

```java
MessageCommand command = MessageCommand.of(
        "sca4dj.event",
        "announcement.published",
        "announcement.published",
        Map.of("announcementId", "2001"))
        .setBusinessKey("2001");

messagePublisher.publishReliable(command);
```

## 消息结构

`MessageEnvelope` 包含消息 ID、事件名、来源、业务 key、时间、Payload、Headers 与 Trace ID。事件 Payload 使用独立类型，避免数据库 Entity 字段变化直接改变消息契约。

延迟发送通过 `MessageCommand.setDelay(Duration)` 和 RabbitMQ delayed exchange 配置使用。Outbox 重试次数和退避参数统一来自消息配置。

## Outbox

`OUTBOX` 模式在业务事务中写入 `sys_message_outbox`。投递器按批次领取记录，发送后等待 RabbitMQ confirm，并更新为：

```text
PENDING -> SENDING -> SENT
                  -> FAILED -> SENDING
                  -> DEAD
```

领取过程包含发送租约，多实例可以同时运行投递器。失败记录按照退避时间重试，达到最大次数后进入 `DEAD`。

`SENT` 表示 broker 已确认接收。消息消费方根据自身业务定义处理成功、重试和去重方式。

## 默认拓扑

应用可以声明默认 exchange、queue、routing key、dead-letter exchange 和 dead-letter queue。相关参数见[消息配置](/reference/configuration#消息)。
