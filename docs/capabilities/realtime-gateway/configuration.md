# WebRealtimeProperties

源码：`modules/web-realtime/sca4dj-web-realtime/src/main/java/com/sca4d/web/realtime/config/WebRealtimeProperties.java`

WebRealtimeProperties 绑定配置前缀 `sca4dj.web-realtime`。Spring Boot 使用对应的短横线配置键，例如 `sca4dj.web-realtime.stomp.channel-queue-max-bytes`。

| 属性 | 默认值 |
|---|---:|
| `nodeTransport` | `LOCAL` |
| `leaseRenewInterval` | `PT30S` |
| `leaseDuration` | `PT90S` |
| `routingBatchSize` | `500` |
| `nodeMessageTtl` | `PT30S` |
| `heartbeatInterval` | `PT25S` |
| `sseConnectionTimeout` | `PT30M` |
| `connectionQueueCapacity` | `64` |
| `ticketTtl` | `PT10S` |
| `stompConnectTimeout` | `PT10S` |
| `inboundFrameMaxBytes` | `65536` |
| `allowedOrigins` | `http://localhost:5173` |
| `stomp.mode` | `NODE_ROUTED` |
| `stomp.sendBufferMaxBytes` | `262144` |
| `stomp.sendTimeLimit` | `PT10S` |
| `stomp.channelWorkerCount` | `4` |
| `stomp.channelQueueCapacity` | `256` |
| `stomp.channelQueueMaxBytes` | `16777216` |
| `stomp.connectionQueueCapacity` | `64` |
| `stomp.connectionQueueMaxBytes` | `1048576` |

`nodeTransport` 接受 `LOCAL` 或 `RABBIT`。`LOCAL` 只投递当前 JVM 的连接，不能用于多个应用实例。`RABBIT` 直接投递本机连接，并通过 RabbitMQ 临时节点队列投递其他实例的连接。

时长和数量必须为正数，Origin 列表不能为空且不能包含空白项。`leaseDuration`、`heartbeatInterval`、`sseConnectionTimeout` 和 `ticketTtl` 以毫秒计算，不能短于 1 毫秒或超过 Java `long` 的毫秒表示范围。`nodeMessageTtl`、`stompConnectTimeout`、`stomp.sendTimeLimit` 和 `stomp.relay.startupConnectTimeout` 最终传给 Spring、RabbitMQ 或 Socket 的 int 类型毫秒参数，必须在 1 至 2147483647 毫秒之间。

`NODE_ROUTED` 使用 Spring simple broker。`BROKER_RELAY` 使用 `stomp.relay.host`、`port`、`clientLogin`、`clientPasscode`、`systemLogin`、`systemPasscode`、`virtualHost` 和 `startupConnectTimeout` 连接 RabbitMQ STOMP plugin。`stomp.relay.port` 必须在 1 到 65535 之间。relay 未配置时默认连接 `localhost` 的 `61613` 端口，`virtualHost` 为 `/`，`startupConnectTimeout` 为 `PT3S`。生产环境必须配置完整的实际 Origin。relay 凭据不能写入版本库。

STOMP 入站、出站分别维护工作线程和容量预算。同一方向内，同一连接的消息串行执行，不同连接可以并发。`channelQueueCapacity` 限制当前方向全部连接尚未完成的消息数，`connectionQueueCapacity` 限制其中单个连接的消息数。`channelQueueMaxBytes` 和 `connectionQueueMaxBytes` 按载荷字节数限制内存；文本载荷按 UTF-8 最大可能字节数保守计入。通道和连接预算不能替代单帧限制，也不是整个 JVM 的内存上限。数量与字节预算都包含正在执行的消息，一条消息的全部通道订阅处理器执行结束后才归还预算。

`channelWorkerCount` 允许 1 至 64；两项消息容量允许 1 至 65536；两项字节预算允许 1 字节至 1 GiB。单连接设置超过通道设置时，仍受通道总预算约束。

容量不足时，服务端拒绝当前消息，以 WebSocket `1013 SERVICE_OVERLOAD` 关闭受影响连接，并清除该连接未执行的消息。已经执行中的业务动作按其事务规则结束。浏览器应重新申请票据并重连，再通过 HTTP 重新读取状态。浏览器不能把断连理解为命令一定没有执行。Transport 关闭后的 `DISCONNECT` 清理不占业务队列预算，确保 Broker 可以删除连接和订阅。

## 示例

以下示例对应一个工作区文档协作应用。应用在两个 Server 实例之间共享 Web Realtime 通知，并通过 Gateway 对外提供入口。

跨实例投递使用 `RABBIT`：

```yaml
sca4dj:
  web-realtime:
    node-transport: RABBIT
    allowed-origins:
      - https://workspace.example.com
    heartbeat-interval: PT25S
    stomp:
      mode: NODE_ROUTED
      channel-worker-count: 4
      channel-queue-capacity: 256
      channel-queue-max-bytes: 16777216
```

`allowed-origins` 写浏览器实际访问应用时使用的 Origin。生产环境必须写实际地址，不使用开发默认值。

需要 RabbitMQ STOMP plugin 负责 `/queue` 和 `/topic` 时，启用 `BROKER_RELAY`。凭据从部署环境读取：

```yaml
sca4dj:
  web-realtime:
    stomp:
      mode: BROKER_RELAY
      relay:
        host: rabbitmq.internal
        port: 61613
        client-login: ${WEB_REALTIME_RELAY_CLIENT_LOGIN}
        client-passcode: ${WEB_REALTIME_RELAY_CLIENT_PASSCODE}
        system-login: ${WEB_REALTIME_RELAY_SYSTEM_LOGIN}
        system-passcode: ${WEB_REALTIME_RELAY_SYSTEM_PASSCODE}
        virtual-host: /
        startup-connect-timeout: PT3S
```

启动前检查配置是否能够被绑定。非法值会在启动阶段失败，例如 `stomp.relay.port` 超出范围时抛出 `IllegalArgumentException`。
## 扩展方式

本页配置由 Web Realtime 模块固定提供，具体应用不需要实现扩展接口。需要改变节点投递或 broker 方式时，修改上述配置键即可。Spring 在启动时读取 `sca4dj.web-realtime.*` 并装配对应组件。修改配置后需要重启 Server 实例。
