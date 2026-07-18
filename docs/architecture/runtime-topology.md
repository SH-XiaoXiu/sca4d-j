# 多实例运行模型

## 单体集群

多个 `sca4d-server` 实例运行同一个 JAR，并连接同一 PostgreSQL、Redis、RabbitMQ 和对象存储。数据库记录和文件不能只保存在某个实例的本地内存或临时目录中。

```text
              ┌─ server A ─ local cache / SSE connections
client ─ GW ──┤
              └─ server B ─ local cache / SSE connections
                    │
        PostgreSQL / Redis / RabbitMQ / MinIO
```

## 共享会话和授权

Sa-Token 登录态和授权快照使用 Redis 共享，使任一实例可以恢复 Session。Principal 仍按请求选择；权限或状态变化时失效共享快照并提示客户端刷新。

## 分布式并发控制

数据库乐观锁处理同一行的并发更新，Redis 幂等记录处理同一请求重复发送，Redisson 锁限制多个实例同时执行某个操作，Redis 限流记录所有实例共享的请求次数。数据库唯一约束仍负责防止重复数据。

## 可靠消息

DIRECT 发送不等待事务；AFTER_COMMIT 在本进程提交后发送；OUTBOX 将待发送消息与业务事务共同提交，由 dispatcher claim、发送、重试。claim version 防止多个实例重复占有同一记录，消费者仍需幂等。

## 跨节点实时投递

连接保存在接入节点本地。`RealtimePublisher` 将目标和事件封装后，经 RabbitMQ 广播到各实例；每个实例只向本地匹配的 SSE 或 STOMP 连接投递。客户端必须能在断线后重新订阅或重新拉取权威状态。

## 后台任务竞争

Quartz 使用 PostgreSQL JDBC JobStore 让多个实例竞争触发器。Subject 清理、Outbox 投递和文件清理由固定系统 Job 触发；业务 Schedule 使用 `schedule_execution.fire_key` 防止同一次触发重复执行。任务处理数据时仍使用 claim、版本或唯一键，不能只依赖某个实例的内存状态。

## 网关与实例上下线

网关从 upstream 文件加载实例，主动健康检查并只路由健康节点。配置热更新失败时保留上一个有效快照。发布时先让实例停止接收新请求、等待在途工作，再关闭进程。

## 失败恢复

- 数据库失败：写入失败并回滚，不返回成功；
- Redis 失败：依赖共享会话或并发设施的操作按关键性失败；
- RabbitMQ 失败：OUTBOX 保留消息记录并重试，DIRECT 和 AFTER_COMMIT 记录发送错误；
- MinIO 失败：BootCheck 决定阻止启动或降级，业务不可返回虚假可用 URL；
- 节点失败：客户端重新连接，其他实例继续处理 Outbox 和新请求。
