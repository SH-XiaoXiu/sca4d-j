# 业务消息与可靠发布

`Event` 表示已经发生的业务事实，是 `Message` 的一种。`EventType` 声明稳定编码、协议版本和 payload 类型。`MessageBus` 根据 `DispatchPolicy` 选择立即发布、提交后发布或 Outbox 可靠发布。

## 事务交接

业务 Service 在事务中完成写入后，调用 `MessageBus.dispatch(event, DispatchPolicy.outbox())`。业务数据与消息信封一起提交或回滚。

Outbox 保存的是待发布动作。返回的消息标识不代表消费者已经执行成功。

`DispatchPolicy.immediate()` 在当前调用中等待 Broker 接受。`DispatchPolicy.afterCommit()` 在提交后尽力发送一次，进程退出或发送失败时可能丢失消息。

需要延迟但可靠的发布时，使用 `DispatchPolicy.outboxNotBefore(time)`。它限制最早派发时间，不保证准确到时执行。

## 重复与失败

RabbitMQ 已接收消息，但发送方尚未记录成功时，同一业务消息可能再次发送。`EventHandler` 和 `MessageHandler` 必须使用原消息 ID 或稳定业务唯一键保护副作用。

Handler 通过 `RetryRule` 声明次数和退避，并通过 `HandlingDecision` 确认、重试或拒绝。

决定允许再次处理时，Messaging 提交定向 Outbox，然后确认原消息。到期后，消息只投递给原 Handler。明确拒绝或次数耗尽后，消息进入对应 Handler 的独立死信队列（DLQ）。

普通 Outbox 发布和消费重试使用相同派发机制，但 Broker 发布次数与业务消费次数分开。完整交接顺序、去重保留期和失败结果见 [Messaging 模块](/capabilities/messaging-notification/messaging)。

## 其他事件路径

Web Realtime 面向当前在线浏览器，允许丢失，不回放。Web Realtime 与 Messaging 共用 `Event` 表达事实。两者使用同一对象类型，但这不表示 Web Realtime 具有可靠消费和重试保证。

MQTT 接入、设备会话、命令 ACK 和在线状态由 IoT 模块负责。IoT 需要可靠跨模块处理时，通过 `MessageBus` 发布标准消息。设备协议和业务重试条件仍保留在 IoT 状态机中。

## 示例

以下示例为 Member 加入 Workspace 的事务。Workspace 与 Member 的表结构和仓储由具体应用定义，示例省略 import 语句。

```java
// 事件类型：编码、协议版本和 payload 类型在声明处固定。
public record MemberJoined(long workspaceId, long memberId) {

    public static final EventType<MemberJoined> TYPE = new EventType<>(
            "WORKSPACE/MEMBER/JOINED", 1, MemberJoined.class);
}

// 由具体应用定义的 Service。
@Service
class WorkspaceMembershipService {

    private final MembershipRepository memberships;
    private final MessageBus messageBus;
    private final Clock clock;

    WorkspaceMembershipService(MembershipRepository memberships, MessageBus messageBus, Clock clock) {
        this.memberships = memberships;
        this.messageBus = messageBus;
        this.clock = clock;
    }

    @Transactional
    public void join(long workspaceId, long memberId) {
        // 业务写入与 Outbox 记录在同一事务中提交或回滚。
        memberships.insert(workspaceId, memberId);
        Event<MemberJoined> event = new Event<>(
                MemberJoined.TYPE,
                workspaceId + ":" + memberId,
                clock.instant(),
                new MemberJoined(workspaceId, memberId));
        messageBus.dispatch(event, DispatchPolicy.outbox());
    }
}
```

事务回滚时，`memberships.insert` 与 Outbox 记录一起回滚，消息不会被发布。事务提交后，派发器再把消息发布到 RabbitMQ。

## 相关页面

- [Messaging 模块](/capabilities/messaging-notification/messaging)
- [Outbox 派发与清理](/capabilities/messaging-notification/outbox)
- [消息开发规则](/capabilities/messaging-notification/development)
- [消息内容与投递结果](/capabilities/messaging-notification/message-content)
