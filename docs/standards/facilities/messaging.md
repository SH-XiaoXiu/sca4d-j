# 消息规则

## 发布入口

业务代码必须通过 `MessagePublisher` 发布，禁止直接调用 RabbitTemplate 或自行声明一套 envelope。

## 模式选择

DIRECT 仅用于与事务无关的发送；AFTER_COMMIT 用于允许提交后因进程退出而丢失的刷新信号；不可丢失的业务事件必须使用 OUTBOX。禁止事务提交前发布“已成功”事件。

## 消息契约

Envelope 必须包含 messageId、type、time、traceId 和类型确定的 payload。禁止序列化 Entity、Request、View 或敏感凭据。

## 消费结果

消费者默认面对至少一次投递，必须幂等。确认、重试、死信和不可恢复错误必须有明确策略；禁止 catch 后静默成功。

## Outbox

Outbox 记录与业务数据在同一事务写入。dispatcher 使用 claim version 和状态竞争，发送失败后可以重试。禁止用本地内存队列替代。

## 拓扑

交换机、routing key 和队列在消息配置或部署脚本中声明。事件名由发布方与消费方共同使用，不在业务类启动时临时创建交换机和队列。
