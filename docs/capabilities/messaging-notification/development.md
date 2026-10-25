# 消息开发规则

本页列出业务代码使用消息功能时必须遵守的规则。每条规则的原因见对应功能页。

## 发布入口

业务代码通过 `MessageBus.dispatch(message, policy)` 发布消息。禁止直接调用 `RabbitTemplate`，也禁止自行定义一套消息信封（envelope）。

## 模式选择

`DispatchPolicy.immediate()` 用于与事务无关的发送。`DispatchPolicy.afterCommit()` 只用于允许在提交后因进程退出而丢失的刷新信号。不可丢失的业务事件必须使用 `DispatchPolicy.outbox()`。禁止在事务提交前发布「已成功」事件。

## 消息格式

消息信封由 `RabbitMessageEnvelopeCodec` 生成。信封字段为 `envelopeVersion`、`messageId`、`messageCode`、`protocolVersion`、`publishedAt`、`sourceApplication` 和 `payload`。`payload` 为消息 Codec 输出的 Base64 文本。

消息内容必须由 `MessageCodec` 编码。禁止序列化 Entity、Request、View 或敏感凭据。

## 消费结果

消费者默认面对至少一次投递。消费者必须幂等。

确认、重试、死信和不可恢复错误必须有明确策略。禁止 `catch` 后静默成功。

## Outbox

Outbox 记录与业务数据在同一事务中写入。派发器通过 `version` 和状态竞争领取记录。发送失败后可以重试。禁止用本地内存队列替代 Outbox。

## 拓扑

交换机、`routing key` 和队列由 `RabbitMessageTopology` 根据 Handler 声明登记，框架在启动时声明这些对象。业务类不在启动时或消费路径中临时创建交换机和队列。

事件名由发布者与消费者共同使用。

## 示例

以下示例为 Member 邀请通知的消费结果。临时错误返回重试，不可恢复错误返回拒绝。两种结果都不静默确认。省略 import 语句。`invitations`、`TemporaryMailException` 与 `InvalidInvitationException` 由具体应用定义。

```java
// 由具体应用定义。Member 邀请通知的消费结果处理。
@Override
public HandlingDecision handle(IncomingMessage<Event<MemberInvited>> message) throws Exception {
    try {
        invitations.deliver(message.messageId(), message.message().payload());
        return HandlingDecision.acknowledge();
    } catch (TemporaryMailException error) {
        return HandlingDecision.retry("邮件服务暂时不可用");
    } catch (InvalidInvitationException error) {
        return HandlingDecision.reject("邀请数据无效，不再重试");
    }
}
```

## 相关页面

- [Messaging 模块](/capabilities/messaging-notification/messaging)
- [业务消息与可靠发布](/capabilities/messaging-notification/domain-events)
- [消息内容与投递结果](/capabilities/messaging-notification/message-content)
