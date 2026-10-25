# Messaging 配置

源码：`modules/messaging/sca4dj-messaging/src/main/java/com/sca4d/messaging/config/MessagingProperties.java`

`MessagingProperties` 绑定配置前缀 `sca4dj.messaging`，用于配置 publisher confirm（发布者确认）与 Outbox 派发。

| 属性 | 默认值 |
|---|---:|
| `confirmTimeout` | `PT10S` |
| `dispatchInterval` | `PT1S` |
| `dispatchBatchSize` | `100` |
| `leaseDuration` | `PT1M` |
| `initialBackoff` | `PT1S` |
| `maximumBackoff` | `PT5M` |
| `sentRetention` | `P7D` |
| `cleanupInterval` | `PT1H` |
| `cleanupBatchSize` | `500` |

所有值必须为正数。

`confirmTimeout`、`leaseDuration`、`initialBackoff`、`maximumBackoff` 和 `sentRetention` 按毫秒使用。它们不能短于 1 毫秒，也不能超过 Java `long` 的毫秒表示范围。

`dispatchInterval` 和 `cleanupInterval` 定义无记录 `JobSchedule` 的固定间隔。每次触发时直接扫描 Outbox，不创建 Job Run。

退避只用于 Outbox 发布失败，不用于消费者重试。派发没有最大尝试次数，也没有 `DEAD` 状态。

Handler 通过 `retryRule()` 配置消费次数和退避，支持固定、指数、序列和无限规则。

消费重试的最早执行时间保存在定向 Outbox 中。实际发布时间还受派发扫描间隔（`dispatchInterval`）和 Broker 可用性影响。

发布失败仍使用本页的初始和最大退避配置，与消费尝试次数分开。`SENT` 保留期也限定已登记消费尝试的去重窗口。

## 示例

以下配置写出全部键及默认值。`sentRetention` 与 `cleanupInterval` 的取值会同时影响 `SENT` 记录的清理和消费重试的去重窗口。

```yaml
sca4dj:
  messaging:
    confirm-timeout: PT10S
    dispatch-interval: PT1S
    dispatch-batch-size: 100
    lease-duration: PT1M
    initial-backoff: PT1S
    maximum-backoff: PT5M
    sent-retention: P7D
    cleanup-interval: PT1H
    cleanup-batch-size: 500
```

## 相关页面

- [Messaging 模块](/capabilities/messaging-notification/messaging)
- [Outbox 派发与清理](/capabilities/messaging-notification/outbox)
