# 实时通信

`RealtimePublisher` 向在线客户端发送业务事件。业务模块只指定目标、事件名和数据，连接所在节点与传输协议由实时基础设施处理。

## 投递目标

| 目标 | 适用范围 |
| --- | --- |
| `SUBJECT` | 同一 Subject 的所有连接 |
| `PRINCIPAL` | 指定类型与 ID 的执行主体 |
| `TENANT` | 一个租户内的连接 |
| `TOPIC` | 业务定义的公共主题 |
| `CONNECTION` | 单个连接 |

```java
realtimePublisher.sendToTenant(
        tenantId,
        "announcement.published",
        new AnnouncementPublishedView(announcementId, title));
```

`send(...)` 同时投递 WebSocket 与 SSE。`RealtimePublishRequest.sse(...)` 和 `websocketUserQueue(...)` 可以指定传输方式。

默认发布器检测到当前存在 Spring 事务时，会登记 `afterCommit` 回调，只有事务成功提交才真正发送；没有事务时立即发送。实时事件用于提示在线客户端更新，客户端仍应能通过重新查询恢复最终状态。

已经在 Spring `afterCommit` 回调中完成数据库读取、Session 替换或缓存清理的代码使用 `publishNow(...)` 立即发送。该方法不会再次登记事务回调，不能在尚未提交的业务事务中调用。

## 建立订阅

客户端先读取订阅信息：

```http
GET /realtime/subscription
Authorization: <token>
```

响应提供当前 Subject、Principal、Tenant 和公共 `client-state` 主题。SSE 入口为：

```http
GET /realtime/sse
```

两个状态刷新事件使用 SSE：

| 事件 | 目标 | 客户端处理 |
| --- | --- | --- |
| `authentication.session.changed` | 受影响 Subject | 重新读取当前 Principal；不可用时回退 DEFAULT |
| `system.menus.changed` | `client-state` 主题 | 重新读取 `/me.navigation`；菜单管理页重新读取菜单树 |

事件只说明状态发生变化，不包含 Subject 权限、菜单树或其他敏感数据。

WebSocket/STOMP endpoint 为 `/realtime/ws`，应用目的地前缀为 `/app`，实时主题位于 `/topic/realtime/{routingKey}`。

## 连接授权

握手阶段恢复 Session，STOMP 帧进入时建立 `CurrentRequest`。订阅拦截器只允许连接订阅服务端计算出的 Subject、Principal、Tenant 和 Connection 目标。

STOMP 的 CONNECT、SUBSCRIBE 和 SEND 分别使用帧级限流，防止长连接绕过 HTTP 入口的流量限制。

## 多实例投递

WebSocket 使用 STOMP broker relay。SSE 连接保存在实际接入节点，RabbitMQ 根据节点队列把事件转发到持有连接的实例。

SSE 客户端需要处理连接超时、心跳和重连。服务端在消息发送或心跳发现客户端已断开时，会移除本机连接并正常结束 emitter，不把客户端断连交给应用异常响应链。浏览器原生 EventSource 无法设置自定义请求头；管理端使用 `eventsource-client` 发送 Token Header，并保留最近事件 ID。服务端不保存回放记录，因此每次成功重连后必须重新读取 `/me`。401 应关闭连接并清理登录态。
