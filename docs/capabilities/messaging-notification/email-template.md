# 邮件模板管理

本页说明示例应用的 Email 模板管理接口。接口路径前缀为 `/system/email/templates`。具体应用可以保留这些路径，也可以定义自己的管理接口。

模板属于 Email 渠道，按 Locale 保存 `subject`、`text` 和 `html` 三种源码。

Renderer 将源码和变量生成主题、纯文本和安全 HTML。管理端可以编辑源码，并预览渲染结果。渲染结果不能作为模板再次保存。

默认 Locale 来自 `LocaleCatalog.defaultLocale()`。报告和模板服务不硬编码 `en-US`。

内置模板的基线保存在应用 classpath 的 `email/templates/` 资源中。应用启动时，只补齐数据库中缺失的内置模板，不覆盖已经保存的修改。实际发送始终读取数据库模板。

具有模板管理权限的账号可以调用恢复基线接口。恢复基线使用当前模板的强 ETag。它将全部内置 Locale 恢复为资源内容，保留启用状态，并递增数据库版本。自定义通知编码没有资源基线，不显示恢复操作。

生产环境当前只启用默认英文 Locale。资源和数据库模型仍保留多语言结构。

## 接口

请求使用以下字段：

| 字段 | 说明 |
|---|---|
| `notificationCode` | 已注册 NotificationType 的稳定编码 |
| `locales` | Locale 到 `subject`、`text`、`html` 源码的对象 |

创建默认停用模板：

```http
POST /system/email/templates
```

创建和完整替换使用 `locales` 中的默认 Locale。替换 Email 模板时，服务端只更新默认 Locale，并保留已经保存的其他 Locale。

完整替换使用 `PUT /system/email/templates/{templateId}` 接口，并携带当前资源的 `If-Match`。启停使用 `PATCH /system/email/templates/{templateId}/status` 接口，并携带 `If-Match`。只允许删除已停用的模板。

翻译中心使用以下子资源：

```http
GET /system/email/templates/{templateId}/i18n
PUT /system/email/templates/{templateId}/i18n
```

翻译子资源返回完整 `locales` 和强 ETag。写入必须携带 `If-Match`，并且只能增加、修改或删除非默认 Locale。提交的默认 Locale 必须与当前资源一致。

恢复基线使用以下接口：

```http
PUT /system/email/templates/{templateId}/baseline
```

草稿预览使用以下接口：

```http
POST /system/email/templates/previews
```

请求包含以下字段：`source` 对象（含 `subject`、`text` 和 `html` 三个源码字段）、`languageTag` 和 `variables` 对象。响应返回 `subject`、`text` 和安全 `html`。

测试发送使用已保存的模板、当前启用的 SMTP 配置和指定的收件邮箱：

```http
POST /system/email/templates/{templateId}/test-messages
```

## 变量规则

变量使用 `{{name}}` 语法保存，不能改写成渲染后的变量值。

预览允许缺失变量。缺失值渲染为空字符串。

实际发送和测试发送要求模板引用的变量全部存在。每个变量值不能为空白，且长度不超过 4096 个字符。

无效变量返回模板校验错误，不进入未分类异常。

安全 URL 变量在 HTML 正文中生成带样式的链接。HTML 属性中的 URL 不会嵌套额外链接。

## 示例

以下请求预览一段邀请邮件的正文。`templateId` 与变量由调用方提供。

```http
POST /system/email/templates/previews
Content-Type: application/json

{
  "source": {
    "subject": "You have been invited to {{workspaceName}}",
    "text": "Open {{inviteUrl}} to accept.",
    "html": "<p>Open <a href=\"{{inviteUrl}}\">the invitation</a> to accept.</p>"
  },
  "languageTag": "en-US",
  "variables": {
    "workspaceName": "Quarterly Review",
    "inviteUrl": "https://example.invalid/invite/abc"
  }
}
```

以下请求恢复内置模板的基线。`If-Match` 使用 `GET /system/email/templates/{templateId}` 返回的强 ETag。

```http
PUT /system/email/templates/42/baseline
If-Match: <当前模板的强 ETag>
```

## 相关页面

- [Notification Email 模块](/capabilities/messaging-notification/notification-email)
- [渠道模板范围](/capabilities/messaging-notification/notification-template)
- [Notification 模块](/capabilities/messaging-notification/notification)
