# 通知

通知模块保存一条通知消息和每个接收目标的投递状态。接收者可以同时按 Subject 和 Principal 区分，使同一 Subject 在不同工作上下文中获得不同通知。

## 创建通知

```java
Long notificationId = notificationService.create(
        "租户资料已更新",
        "租户“示例公司”的名称已经修改。",
        NotificationCategory.SYSTEM,
        PrincipalType.PLATFORM,
        operatorPrincipalId,
        Map.of("tenantId", tenantId),
        List.of(NotificationRecipient.principal(
                PrincipalType.TENANT, tenantPrincipalId)));
```

标题最长 200 个字符，正文最长 2000 个字符。Payload 用于保存跳转目标或业务补充信息。

## 用户接口

```text
GET  /notifications
GET  /notifications/unread-count
PUT  /notifications/{notificationId}/read-status
PUT  /notifications/read-status
```

列表支持只读取未读通知。读取与已读操作同时校验当前 Subject 和 Principal，其他用户的通知不会出现在结果中。

## 实时更新

通知创建和已读状态变化通过 `NotificationRealtimePublisher` 推送。客户端可以在收到事件后更新通知列表或重新读取未读数。

投递状态为 `UNREAD` 和 `READ`。重复执行已读操作不会产生额外状态变化。

## Schedule 任务

`notification.sse` 是当前第一个可注册 `ScheduleTask`。Schedule 保存标题、正文、分类和明确的 DEFAULT Principal 接收人快照；执行时只跳过已删除 Subject，不动态扩大接收面。它复用 `NotificationService.create` 与 `NotificationRealtimePublisher`，通知、delivery 和执行成功事实位于同一业务事务，SSE 仅在提交后通过 RabbitMQ 向各节点广播。

Quartz JobDataMap 不保存通知正文或接收人。重复或 recovery fire 复用相同 fireKey，因此同一次执行不会创建第二条通知消息。

## 失败结果

创建通知时，标题或正文为空、发送人类型与 ID 只提供一项、接收人为空或接收人信息不完整，返回 400。同一接收目标会先去重，再写入一条投递记录。

读取不属于当前 Subject 和 Principal 的通知时返回 `NOTIFICATION_NOT_FOUND`。创建通知和全部投递记录在同一事务中保存；事务回滚时不发送实时事件。
