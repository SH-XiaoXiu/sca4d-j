# 渠道模板范围

Notification 模块只声明通知编码、渠道、Locale 和变量来源。它不保存模板正文，也不定义通用模板格式。

Email 模板由 `modules/notification/sca4dj-notification-email` 拥有。`EmailTemplateRepository` 保存 `EmailTemplateSource`。每个 Locale 分别包含 `subject`、`text` 和 `html` 源码。`EmailRenderer` 将源码和变量渲染为 `RenderedEmail`。模板表为 `notification_email_template`，由 Email 模块的 schema 提供。

其他渠道需要模板时，在各自渠道模块中定义自己的模板来源、Renderer 和持久化方式。Notification 只把已解析的变量和 `Content` 交给渠道投递。它不要求渠道共享 Email 的模板或 MIME 模型。

JSON 只出现在 HTTP 接口、数据库读写和 Codec 转换中。Renderer 接收已经解码的内容对象。预览允许变量缺失并渲染为空。实际投递时，由渠道或业务服务按各自的可靠性规则校验变量。

## 示例

以下示例展示通知变量如何流向 Email 模板。Notification 层只提供变量名与值，模板中的 `{{name}}` 由 Email 渲染器替换。省略 import 语句。

```java
// 通知类型声明变量来源。变量名须与 Email 模板中的 {{workspaceName}} 一致。
NotificationType<WorkspaceInvitation> invitation = NotificationType.define(
        "WORKSPACE/MEMBER/INVITATION",
        WorkspaceInvitation.class,
        definition -> {
            definition.required(EmailChannels.EMAIL);
            definition.variable("workspaceName", (value, locale) -> value.workspaceName());
        },
        (value, execution) -> execution.sendToAccount(value.memberAccountId()));
```

对应的 Email 模板源码（`text` 部分）如下。预览时，缺失的变量渲染为空字符串。实际发送时，模板引用的变量必须全部存在。

```text
You have been invited to {{workspaceName}}.
```

## 扩展方式

其他渠道需要模板时，在渠道模块中定义模板来源、Renderer 和持久化方式，并提供该渠道的 `NotificationDelivery` Bean。通知层不读取渠道模板，只把已解析的变量和 `Content` 交给渠道。

## 相关页面

- [Notification 模块](/capabilities/messaging-notification/notification)
- [Notification Email 模块](/capabilities/messaging-notification/notification-email)
- [邮件模板](/capabilities/messaging-notification/email-template)
- [站内信](/capabilities/messaging-notification/inbox)
