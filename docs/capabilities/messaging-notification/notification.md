# Notification 模块

`modules/notification/sca4dj-notification` 负责类型化通知、Locale 选择、渠道编排和可靠异步发布。Notification 模块不拥有 Email、SMS 或 Realtime 的渠道常量和模板格式。具体渠道模块使用 `TreeCode` 声明渠道，并提供自己的 Renderer、Codec 和 Delivery 实现。

业务代码通过 `NotificationType` 声明稳定通知编码、必发渠道和模板变量。随后，通过 `Notifications.enqueue(type, value)` 登记可靠通知。业务代码不直接组装通知变量 Map，也不读取通知设置。

示例应用注册的通知类型如下：

| 编码 | 用途 |
|---|---|
| `PLATFORM/ACCOUNT/INVITATION` | 账号邀请 |
| `PLATFORM/ACCOUNT/PASSWORD/RESET` | 管理员发起的密码重置 |
| `AUTHENTICATION/EMAIL/CODE` | Email 登录验证码 |
| `PLATFORM/PROFILE/EMAIL/VERIFICATION` | 新邮箱确认 |
| `PLATFORM/PROFILE/EMAIL/CHANGED` | 旧邮箱安全提醒 |

示例应用的所有通知类型都把 Email 渠道声明为必发渠道。

一次通知可以选择多个渠道。Notification 层只生成一条 `NotificationMessage`，它携带选中的渠道集合。每个渠道的 `NotificationMessageHandler` 各自消费这条消息，只处理自己负责的渠道。

同步发送（`Notifications.sendNow`）时，Notification 层检查必发渠道是否可用，例如 Email 收件人、启用的 SMTP 配置和对应的启用模板。必发渠道不可用时抛出 `NotificationException`。`Notifications.enqueue` 不做这项检查，渠道可用性在消息消费时由 `NotificationDeliveryRouter` 判断。

通知语言由 `NotificationLocaleResolver` 解析。示例应用当前使用 `LocaleCatalog.defaultLocale()`。开发环境可以切换应用默认 Locale，生产环境使用英文默认 Locale。模板本身仍然可以保存多个 Locale，预览和测试发送可以指定目标 Locale。

通知类型的回调只负责确定接收人、Locale、领域变量和可选的 `Content`。

`Notification` 是框架内部冻结的结果。它包含接收人、Locale、变量、`Content`、业务区域和已选渠道。

可靠通知写入 Messaging Outbox 后，`MessageId` 只表示请求已经登记。Email 的服务端确认（ACK）由 SMTP 渠道提供。Outbox 确认不代表收件人已经阅读通知。

## 示例

以下示例声明 Member 收到 Workspace 邀请的通知类型，并在业务事务中登记通知。省略 import 语句。`EmailChannels` 位于 `modules/notification/sca4dj-notification-email`，`SmsChannels` 位于 `modules/notification/sca4dj-notification-sms`。

```java
// 通知的领域值。
public record WorkspaceInvitation(long memberAccountId, String workspaceName, String inviteUrl) {
}

@Configuration
class WorkspaceNotificationConfiguration {

    @Bean
    NotificationType<WorkspaceInvitation> workspaceInvitation() {
        return NotificationType.define(
                "WORKSPACE/MEMBER/INVITATION",
                WorkspaceInvitation.class,
                definition -> {
                    // 必发渠道：Email。可选渠道：SMS，默认不启用。
                    definition.required(EmailChannels.EMAIL);
                    definition.optional(SmsChannels.SMS, false);
                    // 模板变量名须匹配 [a-z][a-zA-Z0-9_]{0,63}。
                    definition.variable("workspaceName", (value, locale) -> value.workspaceName());
                    definition.variable("inviteUrl", (value, locale) -> value.inviteUrl());
                },
                // 回调只确定接收人、Locale 与可选内容。
                (value, execution) -> execution.sendToAccount(value.memberAccountId()));
    }
}

// 由具体应用定义的 Service。
@Service
class WorkspaceInvitationService {

    private final Notifications notifications;
    private final NotificationType<WorkspaceInvitation> invitation;

    WorkspaceInvitationService(Notifications notifications, NotificationType<WorkspaceInvitation> invitation) {
        this.notifications = notifications;
        this.invitation = invitation;
    }

    @Transactional
    public void invite(long memberAccountId, String workspaceName, String inviteUrl) {
        // 返回值是消息标识，只表示请求已经登记到 Outbox。
        notifications.enqueue(invitation, new WorkspaceInvitation(memberAccountId, workspaceName, inviteUrl));
    }
}
```

`enqueue` 返回的消息标识不代表邮件已发送或已阅读。发送结果由渠道的确认和重试机制决定。通知登记与业务写入在同一事务中提交。

## 扩展方式

应用通过 `NotificationType.define` 声明通知类型，并把每个类型注册为 Spring Bean。通知层通过 `List<NotificationType<?>>` 收集全部类型，并按编码建立目录。

应用或渠道模块为 `NotificationDelivery` 提供 Bean。通知层通过 `List<NotificationDelivery>` 收集渠道，并按 `channel()` 与 `available(notification)` 选择。

## 相关页面

- [Notification Email 模块](/capabilities/messaging-notification/notification-email)
- [通知模板](/capabilities/messaging-notification/notification-template)
- [消息内容与投递结果](/capabilities/messaging-notification/message-content)
- [短信通知渠道](/capabilities/messaging-notification/sms)
- [站内信](/capabilities/messaging-notification/inbox)
- [Messaging 模块](/capabilities/messaging-notification/messaging)
