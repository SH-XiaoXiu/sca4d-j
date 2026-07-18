# 消息与实时通信

## 消息发布

包：`com.sca4d.common.messaging`

| 类型 | 主要方法或字段 | 默认实现与限制 |
| --- | --- | --- |
| `MessagePublisher` | `publish(...)`、`publishDirect(...)`、`publishAfterCommit(...)`、`publishReliable(...)` | `DefaultMessagePublisher`；发布失败抛出 `MessagePublishException` |
| `MessageCommand` | `of(...)`；exchange、routingKey、event、payload、headers、businessKey、delay、mode、retryPolicy | 默认 `DIRECT`，默认 delay 为 0 |
| `MessagePublishMode` | `DIRECT`、`AFTER_COMMIT`、`OUTBOX` | `AFTER_COMMIT` 仅在事务提交后发送；`OUTBOX` 先保存数据库记录 |
| `MessageEnvelope<T>` | `from(...)`；messageId、event、businessKey、traceId、source、publishedAt、headers、payload | RabbitMQ 消息格式 |
| `MessageRetryPolicy` | `backoff(...)` | 按部署配置计算 Outbox 重试退避 |

## Outbox

包：`com.sca4d.common.messaging.outbox`

| 类型 | 主要方法或状态 | 默认实现与失败行为 |
| --- | --- | --- |
| `MessageOutboxDeliveryService` | 领取并发送待处理记录 | `DefaultMessageOutboxDeliveryService` |
| `MessageOutboxDispatcher` | 定时触发发送 | 多实例通过数据库领取避免重复发送 |
| `MessageOutboxRepository` | 保存、领取、成功、重试和失败更新 | 最终状态为 `SENT` 或 `DEAD` |
| `MessageOutboxStatus` | `PENDING`、`SENDING`、`SENT`、`FAILED`、`DEAD` | 发送失败先进入 `FAILED`，超过最大尝试次数进入 `DEAD` |

RabbitMQ 发送实现为 `com.sca4d.common.messaging.rabbit.RabbitMessageSender`。默认 exchange、queue 和 routing key 常量位于 `RabbitMessagingTopology`。

## 实时发布

包：`com.sca4d.common.realtime`

| 类型 | 主要方法 | 默认实现与限制 |
| --- | --- | --- |
| `RealtimePublisher` | `publish(...)`、`publishNow(...)`、`send(...)`、`sendToSubject(...)`、`sendToPrincipal(...)`、`sendToTenant(...)`、`sendToTopic(...)` | `DefaultRealtimePublisher`；`publish(...)` 等待当前事务提交，`publishNow(...)` 只用于已经完成提交的回调 |
| `RealtimePublishRequest` | `all(...)`、`of(...)`、`websocketUserQueue(...)`、`sse(...)` | transport 集合为空时不投递 |
| `RealtimeTarget` | `subject(...)`、`principal(...)`、`tenant(...)`、`topic(...)`、`connection(...)` | topic 会执行格式校验和规范化 |
| `RealtimeConnectionTargetResolver` | 解析连接允许订阅的目标 | `DefaultRealtimeConnectionTargetResolver` |
| `RealtimeEnvelope` | event、target、data、traceId、metadata | SSE 和 WebSocket 共用的消息内容 |
| `RealtimeTransport` | `SSE`、`WEBSOCKET` | 由 `RealtimePublishRequest` 指定 |
| `AuthenticationRealtimePublisher` | `sessionChanged(subjectId)` | 发布 `authentication.session.changed` |
| `SystemRealtimePublisher` | `menusChanged()` | 向 `client-state` 主题发布 `system.menus.changed` |

## 连接实现

| 包 | 类型 | 作用或限制 |
| --- | --- | --- |
| `com.sca4d.common.realtime.sse` | `SseConnectionRegistry`、`SseConnectionService` | 连接只保存在当前实例；发送或心跳发现客户端断开时移除连接并正常结束 emitter |
| `com.sca4d.common.realtime.sse` | `RabbitSseMessagePublisher`、`SseRealtimeMessageListener` | RabbitMQ 将事件广播到各实例 |
| `com.sca4d.common.realtime.ws` | `StompRequestInterceptor` | 为 STOMP 帧建立并清理 CurrentRequest |
| `com.sca4d.common.realtime.ws` | `StompSubscriptionAuthorizationInterceptor` | 拒绝未授权 destination |
| `com.sca4d.common.realtime.ws` | `StompFrameRateLimitInterceptor` | CONNECT、SUBSCRIBE 和 SEND 分别限流 |

## 路由与连接类型

| 包 | 类型 | 主要成员或结果 |
| --- | --- | --- |
| `com.sca4d.common.realtime` | `RealtimeRoutes` | HTTP、SSE、STOMP 路径常量和 `realtimeTopic(...)` |
| `com.sca4d.common.realtime` | `RealtimeSubscription` | type、value、routingKey、stompDestination |
| `com.sca4d.controllers` | `RealtimeController` | 查询订阅信息并建立 SSE 连接 |
| `com.sca4d.common.realtime.ws` | `RealtimeHandshakeInterceptor` | 在握手阶段恢复 Session 并保存 CurrentRequest、客户端地址和 User-Agent |
| `com.sca4d.common.realtime.ws` | `RealtimeHandshakeHandler` | 根据握手请求建立连接 Principal |
| `com.sca4d.common.realtime.ws` | `RealtimeConnectionPrincipal` | `from(currentRequest)`、`getName()` |
| `com.sca4d.common.realtime.ws` | `RealtimeMessagingExceptionHandler` | 将限流和业务异常转换为 `RealtimeEnvelope<RealtimeErrorPayload>` |
