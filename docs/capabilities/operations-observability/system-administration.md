# 系统管理接口

示例应用 Sca4d Server 通过系统管理路由维护[字典](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/dictionary/package-summary.html)、[设置](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/setting/package-summary.html)、 SMTP 配置和 Email 模板。普通 JSON 外层和错误结构遵守 [HTTP API 通用约定](/reference/http/common)。

本页说明这组路由的权限、版本、数据结构和失败结果。路由的方法、路径和成功状态以 [Sca4d Server HTTP API](/reference/http/sca4d-server) 为准。

## 路由、认证与权限

所有调用都要求在线 Session 能选择唯一 Platform Principal 。字典读取与写入分别检查 `system:dictionaries:read` 和 `system:dictionaries:manage`。设置读取与写入分别检查 `system:settings:read` 和 `system:settings:manage`。 Email 读取与管理分别检查 `system:email:read` 和 `system:email:manage`。

没有 Session 返回 401 。缺少对应权限返回 403 。写入会记录系统审计操作。

`version` 是 Java Long，JSON 响应使用十进制字符串。客户端把读取到的同一字符串作为更新请求正文或删除查询参数提交。版本已经变化时不得自动重试。

## 字典

`GET /system/dictionaries` 的 `data` 包含 `dictionaries` 数组。每一项包含 `key` 、 `label` 、 `codeDefined` 、 `optionsEditable` 、 `enabled` 、 `sortOrder` 、可空 `version` 和 `options`。

选项包含 `value` 、可空 `labelKey` 、可空 `fallbackLabel` 、可空完整 `i18n` 、已经按请求语言转换的 `label` 、 `codeDefined` 、 `enabled` 、 `sortOrder` 和可空 `version`。代码项的 `fallbackLabel` 和 `i18n` 为空。数据库项的 `fallbackLabel` 是数据库 `label` 原值，`i18n` 返回已经保存的完整对象。

`codeDefined=true` 表示字典或选项由 Java Dictionary 枚举声明。代码字典自身不可修改和删除，代码项也不可修改和删除。它们的 `version` 为 `null`。只有 `optionsEditable=true` 的代码字典可以新增数据库项。动态字典和数据库项使用数据库 `version` 做乐观锁。

禁用项仍在管理结果中，以便维护者查看和重新启用。业务新输入应使用下述选择接口的 `disabled` 判断。

`GET /system/dictionaries/{key}/options` 只返回业务表单所需的字段：

```json
{
  "key": "iam.identity-status",
  "options": [
    {
      "value": "ACTIVE",
      "labelKey": "iam.identity-status.active",
      "label": "启用",
      "codeDefined": true,
      "disabled": false
    }
  ]
}
```

服务端先按 `sortOrder` 、再按 `value` 排序。调用方不得自己维护同一枚举的标签或禁用规则。 `iam.identity-status` 与 `iam.role-status` 是封闭代码字典，调用方可以读取，但不能增加数据库项。

创建动态字典的正文为：

```json
{
  "key": "business.priority",
  "label": "业务优先级",
  "enabled": true,
  "sortOrder": 100
}
```

`key` 长度最多 160，必须满足 `[a-z][a-z0-9]*(?:[.-][a-z0-9]+)+`。 `label` 不能为空且最多 160 。 `sortOrder` 是 0 到 1,000,000 的整数。键不能与代码字典或已有动态字典冲突。修改正文省略 `key`，并增加非负 `version`。

创建数据库选项使用 `value` 、 `label` 、 `i18n` 、 `enabled` 与 `sortOrder`：

```json
{
  "value": "HIGH",
  "label": "High",
  "i18n": {
    "zh-CN": {
      "label": "高"
    }
  },
  "enabled": true,
  "sortOrder": 100
}
```

`label` 是不能为空且最多 160 字符的回退文本。 `i18n` 可以是空对象。每个已提供 Locale 只能包含一个必填 `label`，长度最多 160 。 `value` 最多 160，必须匹配 `[A-Za-z0-9][A-Za-z0-9._:-]{0,159}`。同一字典内的数据库值及代码值按大小写不敏感方式唯一。修改正文省略 `value`，并增加非负 `version`。

写入封闭代码字典、代码项、已删除目标或版本已变化的目标时，不会写入部分数据。不存在目标返回 404 。键冲突、向封闭代码字典写入数据库项和乐观锁冲突返回 409 。格式或范围不合法返回 400 。

## 设置

`GET /system/settings` 的 `data.groups` 按设置分组顺序排列，只包含应用允许业务管理员维护的分组。每个分组包含 `key` 、 `labelKey` 、本地化 `label` 、 `sortOrder` 和 `settings`。每个设置包含 `key` 、名称和说明的消息键及本地化文本、 `valueType` 、可空 `value` 、可空 `defaultValue` 、 `stored` 、 `secret` 、 `readOnly` 、 `sortOrder` 与 `version`。公共认证、通知、文件存储和产品元数据设置不通过该管理接口暴露。

`value` 和 `defaultValue` 都是 SystemSetting 自己的 SettingCodec 生成的 JSON 值。字符串、数字和 object 保持对应 JSON 类型，不使用统一文本字段包住 object 。

`secret=true` 时两个字段一律为 `null`，即使数据库已经保存值也不会返回明文或密文。 `stored=true` 表示数据库存在显式覆盖值。否则当前有效值来自 Java 默认值。 `readOnly=true` 表示初始化节点正在按环境变量持续同步，管理接口不能覆盖或恢复默认值。

覆盖正文包含一个完整 JSON 值：

```json
{
  "value": "示例展示名称"
}
```

服务端先找到已注册的 SystemSetting，再通过它自己的 SettingCodec 解析和保存。客户端不能创建任意键，也不能通过这一路由访问 LogicSetting 或 PreferenceSetting 。

设置集合中的每项返回单项 `resource` 和不透明 `etag`。 `GET /system/settings/{key}` 也发布同一强 ETag，条件 PUT 和 DELETE 直接使用对应的资源修订。 DELETE 保留递增版本，并使后续读取回到 Java 默认值。

持续同步的键在覆盖和恢复时返回 409 。版本冲突返回 412 。 Codec 不能解析的 JSON 不会保存。初始化、默认值和敏感值的来源见[字典与设置](/capabilities/data-configuration/dictionaries-and-settings)与 [SettingBootstrap](/javadoc/core/sca4dj-core-spring/com/sca4d/core/spring/setting/SettingBootstrap.html)。

## SMTP 配置

SMTP 配置包含名称、主机、端口、 `NONE|STARTTLS|TLS` 、可选认证用户名与密码、发件邮箱、可选显示名和回复邮箱。用户名与密码必须同时存在或同时为空。创建结果默认停用。数据库只允许一个未删除配置处于启用状态。启用配置不能删除，必须先停用。

密码使用 SecretKeyring 加密后保存到 `notification_email_sender.password_key_id` 与 `password_ciphertext`。所有读取结果只返回 `passwordConfigured`，不返回密码、 keyId 或密文。替换请求使用 `passwordChanged` 明确区分保留现有密码、写入新密码和清除密码。系统不会把空字段解释为密码原文。

`POST /system/email/senders/{senderId}/connection-tests` 使用指定配置建立并关闭 SMTP 连接，不发送邮件。 SMTP 连接、读取和写入超时分别读取以下配置，值必须为正且不超过 JavaMail 可表示的毫秒范围：

| 配置键 | 环境变量 | 默认值 |
|---|---|---:|
| `sca4dj.notification.email.connection-timeout` | `SCA4DJ_NOTIFICATION_EMAIL_CONNECTION_TIMEOUT` | `PT5S` |
| `sca4dj.notification.email.read-timeout` | `SCA4DJ_NOTIFICATION_EMAIL_READ_TIMEOUT` | `PT10S` |
| `sca4dj.notification.email.write-timeout` | `SCA4DJ_NOTIFICATION_EMAIL_WRITE_TIMEOUT` | `PT10S` |

## Email 模板

Email 模板按通知 `TreeCode` 唯一保存源码模板，创建结果默认停用。 Email 模块的 EmailTemplateRepository 读写 `notification_email_template`，每个 Locale 包含 `subject` 、 `text` 和 `html`。内置模板基线来自应用 classpath 的 `email/templates/` 资源。启动时只补齐缺失模板，不覆盖数据库修改。

服务端修改默认 Locale 时，保留其他 Locale 。具有管理权限的账号可以通过 `PUT /system/email/templates/{templateId}/baseline` 和当前强 ETag 恢复资源基线，并保留启用状态。非默认 Locale 通过 `/system/email/templates/{templateId}/i18n` 维护，使用强 ETag，并禁止修改或删除默认 Locale 。

模板接受任意合法 BCP 47 Locale 。静态消息的 `supportedLocales` 不是模板语言白名单。变量使用 `{{name}}` 表达式保存。 Email Renderer 负责替换、 HTML 转义、清洗和 URL 链接。预览缺少变量时替换为空字符串。

测试发送接受最多 100 个变量。每个变量值最多 4096 个字符，且不能为空白。测试发送要求有效收件邮箱和当前启用的 SMTP 配置。

SMTP 配置与 Email 模板的稳定详情读取发布强 ETag 。条件更新和删除必须提交同一资源的 `If-Match`。缺失返回 428，过期返回 412 。启用或停用是针对当前状态的命令，不要求客户端先读取 ETag 。唯一启用约束和内部乐观锁仍在事务中执行。

名称、唯一启用 SMTP 配置或通知编码冲突返回 409 。配置、 Locale 、模板表达式或字段不合法返回 400 。 SMTP 连接或发送失败返回 503 。

## 示例：读取字典选项与更新设置

下面的命令使用 Session Cookie 调用两个接口。 `SESSION` 与 `BASE_URL` 由部署方提供，`ETAG` 必须来自上一次读取的响应头。

```bash
curl -s "${BASE_URL}/system/dictionaries/iam.identity-status/options" \
  -H "Cookie: SESSION=${SESSION}"

curl -s -X PUT "${BASE_URL}/system/settings/example.display-name" \
  -H "Cookie: SESSION=${SESSION}" \
  -H "Content-Type: application/json" \
  -H "If-Match: ${ETAG}" \
  -d '{"value":"示例展示名称"}'
```

如果更新返回 412，客户端必须重新读取设置，并使用新的 ETag 再次判断是否提交。客户端不得自动重放原请求。
## 扩展方式

这组路由由示例应用的 Server 提供。应用新增字典时，通过 Java Dictionary 声明代码字典；新增设置时，注册 SystemSetting 并交由 SettingBootstrap 初始化。应用不应在页面中复制字典标签或禁用规则，服务端返回的选项结果是唯一来源。
