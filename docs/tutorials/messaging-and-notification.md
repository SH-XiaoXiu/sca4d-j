# 发布消息与通知

业务事件通过 MessagePublisher 发送给消费者；面向用户的邮件、站内信和短信通过 Notifications 发送。先确定接收方，再选择发布入口和失败后的处理方式。

## 注册事件并可靠发布

用 `EventType` 声明稳定编码、协议版本和 payload 类型，payload 使用只包含消费所需字段的普通 Java 类。为事件注册 `MessageConsumer`，为每个消费职责使用唯一编码。

在业务 Service 的数据库事务中完成写入后，调用 `MessagePublisher.publishReliably(event)`。该方法要求活跃事务；返回的 MessageId 对应同一事务中的 Outbox 记录。事务回滚时业务写入和 Outbox 一起回滚。

消费者按 MessageId 或业务唯一键防止重复执行。验证时分别检查事务回滚没有留下 Outbox、正常发送进入消费者、同一消息重复投递没有重复修改业务数据。消费失败进入独立 DLQ，修复原因后再决定重新发布或删除。

## 在本地发送一封通知邮件

先启动 Mailpit，再通过 `/system/email/senders` 创建 `host=localhost`、`port=1025`、`security=NONE` 的发送方，测试连接并启用。应用注册 `NotificationType`，声明接收人、变量及 EMAIL 渠道，为该通知编码创建并启用统一通知模板。

通过 `Notifications.enqueue(type, notice)` 在事务中登记发送请求，然后到 `http://localhost:8025` 查看收件结果。MessageId 表示请求已登记；收件箱中的邮件才能证明本地 SMTP 投递完成。接收地址由应用的 `EmailRecipientResolver` 提供。

## 业务消息与可靠发布

业务事件表示已经发生且可以由其他模块异步处理的业务事实。Sca4d-J 使用 [Event](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/event/Event.html) 保存可选业务键、发生时间和 payload，使用 [EventType](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/event/EventType.html) 声明稳定事件编码、协议版本和 payload 类型，使用 [MessagePublisher](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/messaging/MessagePublisher.html) 选择立即发布、事务提交后发布或 Outbox 可靠发布。

Messaging 和 [Web Realtime](/tutorials/realtime-events) 共用 Event 表达业务事实，但它们是两条独立路径。Messaging 面向业务 Consumer，RabbitMQ 持久保存消息；Web Realtime 面向当前在线浏览器，允许丢失且不回放。

### 发布方式

```mermaid
flowchart LR
    Service[业务 Service]
    Tx[(业务事务)]
    Outbox[(messaging_outbox)]
    Publisher[Messaging Publisher]
    Rabbit[(RabbitMQ 持久消息)]
    Consumer[MessageConsumer]
    DLQ[(Consumer 独立 DLQ)]

    Service -->|publishNow| Publisher
    Service -->|publishAfterCommit| Tx --> Publisher
    Service -->|publishReliably| Tx --> Outbox
    Outbox -->|持续派发直到 confirm| Publisher --> Rabbit --> Consumer
    Consumer -->|失败时 reject, requeue=false| DLQ
```

- `publishNow` 立即发送，消息无法路由或 RabbitMQ 未确认时调用失败。
- `publishAfterCommit` 在当前事务提交后发送；它避免回滚事务产生外部消息，但进程可能在提交与发送之间退出，因此不保证最终发布。
- `publishReliably` 在当前事务写入 Outbox。事务回滚时记录一起回滚；事务提交后派发任务持续发送，直到 RabbitMQ 返回 publisher confirm。
- 带 `earliestPublishAt` 的可靠发布不会早于指定时间派发。

Outbox 只保证发布。它不是业务消息存储，不保存 Consumer 处理结果，也不负责消费失败恢复。消息发布到 RabbitMQ 后，持久保存、队列复制和重新投递由 RabbitMQ 负责。

### Outbox 状态与重复消息

Outbox 使用 `PENDING -> SENDING -> SENT` 和 `FAILED -> SENDING`。多实例通过状态、version、可用时间和租约到期条件的 `UPDATE ... RETURNING` 领取记录，不使用显式行锁；实例退出后，其他实例可以接管过期的 `SENDING` 租约。发布失败采用有上限的指数退避并持续尝试，不设置最大次数，也没有发布侧 `DEAD` 状态。

RabbitMQ 已确认而 Outbox 状态更新失败时，同一 `MessageId` 可能再次发布。Consumer 必须使用 `MessageId` 或稳定业务键实现幂等，不能把“通常只收到一次”当作接口保证。`SENT` 记录只用于短期审计和清理，不替代 RabbitMQ 的持久消息。

### Consumer 与死信

每个 [MessageConsumer](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/messaging/MessageConsumer.html) 编码对应一条持久队列和一条独立 DLQ。同一 Consumer 的多个 App 实例竞争消费；不同 Consumer 分别获得一份消息。

Consumer 正常返回后才 ACK。业务异常、payload 反序列化失败或协议版本不匹配时，消息直接 `reject(requeue=false)` 进入该 Consumer 的 DLQ，不经过自动重试队列。人工核对失败原因后，只能选择重新发布或删除死信。Messaging 不自动重放 DLQ，也不提供 Inbox、补偿工作流或 DLQ 管理页面。

进程在 ACK 前退出或 ACK 丢失时，RabbitMQ 可以重新投递原消息。这与 Consumer 处理失败进入 DLQ 是不同情况，两者都要求 Consumer 具备幂等性。

### 设备消息由 IoT 模块处理

MQTT、TCP、设备 WebSocket、设备会话和 EMQX 属于后续 IoT 模块，不进入 Messaging 或 Web Realtime。IoT 只有在标准化后的设备消息需要可靠异步处理、削峰或跨模块消费时才发布到 Messaging；设备协议接入、命令应答和在线状态仍由 IoT 模块负责。

Outbox 派发间隔、租约和清理配置见[MessagingProperties](/reference/runtime/sca4d-server#messagingproperties)。

## Notification 模块

`modules/notification/sca4dj-notification` 把领域通知转换成已经确定接收人、Locale、Region、模板变量和渠道的标准通知。它封装 Settings，业务调用方不读取通知设置键，也不组装字符串变量 Map。

```mermaid
flowchart LR
    Caller[模块或具体应用]
    Type[NotificationType]
    Service[Notifications]
    Outbox[Messaging Outbox]
    Consumer[NotificationRequestedConsumer]
    Router[NotificationDeliveryRouter]
    Inbox[Inbox NotificationDelivery]
    Email[Email NotificationDelivery]
    Sms[SMS NotificationDelivery]

    Caller --> Type --> Service
    Service -->|enqueue| Outbox --> Consumer --> Router
    Service -->|sendNow| Router
    Router --> Inbox & Email & Sms
```

领域代码使用 [NotificationType](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/notification/NotificationType.html) 声明必发渠道、可选渠道及 Java 默认值、模板变量回调和接收人回调。调用方只提交领域对象，完整代码见[接入一种领域通知](#接入一种领域通知)。

[Notifications](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/notification/Notifications.html) 读取 `notification.channel-defaults` 和接收人的 `notification.channel-preferences`。必发渠道不能被设置关闭；可选渠道按接收人覆盖、系统默认和 Java 初值的顺序选择。框架再通过具体应用提供的 `NotificationLocaleResolver` 和 `NotificationRegionResolver` 解析语言与业务区域。业务调用方不会取得 `Settings`、`NotificationCode` 或变量 Map。

[Notification](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/notification/Notification.html) 是框架内部标准结果，保存通知编码、内部接收人、Locale、Region、变量和已经冻结的渠道集合。渠道只解析地址、渲染同一模板并执行自己的外部操作。

`sendNow` 同步执行已经选择的渠道。`enqueue` 把 `notification.requested@2` 写入 Messaging Outbox，返回 MessageId 只表示请求已经可靠登记。Inbox、Email 和 SMS 使用独立 Consumer 身份和 DLQ；一个渠道失败不会让已经成功的其他渠道重复执行。渠道使用原始 MessageId 实现自己的幂等或尝试记录。

### 接入一种领域通知

具体应用先定义普通领域对象，再用 `NotificationType` 声明稳定通知编码、渠道、模板变量和接收人。下面的 `PasswordChanged` 由具体应用定义；`NotificationType` 只读取它的公开业务字段：

```java
/** 保存一次密码修改安全通知需要的 Account 和发生时间。 */
public final class PasswordChanged {

    public static final NotificationType<PasswordChanged> TYPE = NotificationType.define(
            "security.password-changed",
            PasswordChanged.class,
            definition -> {
                definition.required(NotificationChannel.SMS);
                definition.optional(NotificationChannel.EMAIL, true);
                definition.optional(NotificationChannel.INBOX, true);
                definition.variable(
                        "changedAt",
                        (notice, locale) -> notice.changedAt().toString());
            },
            (notice, execution) -> execution.sendToAccount(notice.accountId()));

    private final long accountId;

    private final Instant changedAt;

    /** 创建一次密码修改安全通知。 */
    public PasswordChanged(long accountId, @NotNull Instant changedAt) {
        this.accountId = accountId;
        this.changedAt = Objects.requireNonNull(changedAt, "改密时间不能为空");
    }

    public long accountId() {
        return accountId;
    }

    public @NotNull Instant changedAt() {
        return changedAt;
    }
}
```

每个 `NotificationType` 都必须作为 Spring Bean 注册。`NotificationTypeCatalog` 在应用启动时收集这些 Bean；重复通知编码会使启动失败：

```java
/** 注册具体应用的安全通知类型。 */
@Configuration(proxyBeanMethods = false)
public class SecurityNotificationConfiguration {

    /** 注册改密安全通知。 */
    @Bean
    NotificationType<PasswordChanged> passwordChangedNotificationType() {
        return PasswordChanged.TYPE;
    }
}
```

必发渠道不可由系统或接收人关闭。可选渠道的最终值依次读取接收人覆盖值、`notification.channel-defaults` 和 `NotificationType` 中的 Java 初值。必发渠道当前不可用时，通知发布失败；不可用的可选渠道不进入本次通知。

### 创建并启用统一模板

Sca4d Server 通过 `POST /api/system/email/templates` 管理统一通知模板。路径保留了 Email 管理入口名称，但保存的数据同时供 Email、Inbox 和其他模板渠道使用。下面的请求创建 `security.password-changed` 的 `zh-CN` 模板；实际模板必须包含 `LocaleCatalog` 声明的默认 Locale：

```json
{
  "notificationCode": "security.password-changed",
  "content": {
    "zh-CN": {
      "title": {
        "type": "doc",
        "content": [
          {
            "type": "paragraph",
            "content": [
              { "type": "text", "text": "密码已修改" }
            ]
          }
        ]
      },
      "body": {
        "type": "doc",
        "content": [
          {
            "type": "paragraph",
            "content": [
              { "type": "text", "text": "你的密码修改时间是 " },
              {
                "type": "notificationVariable",
                "attrs": { "name": "changedAt" }
              },
              { "type": "text", "text": "。如非本人操作，请立即联系管理员。" }
            ]
          }
        ]
      }
    }
  }
}
```

模板创建后默认停用。读取 `GET /api/system/email/templates/{templateId}` 的 `ETag`，再发送以下请求启用模板：

```http
PATCH /api/system/email/templates/{templateId}/status
If-Match: "<当前版本>"
Content-Type: application/json

{"enabled":true}
```

模板只能引用对应 `NotificationType` 已声明的变量。模板缺少默认 Locale、`title` 或 `body`，变量名未声明，或者 ProseMirror 结构、节点数量和嵌套深度不合法时，请求失败。

### 选择同步或可靠发送

业务动作已经处于数据库事务中时，使用 `enqueue` 把通知请求和业务写入一起提交。事务回滚时不留下通知 Outbox：

```java
/** 在账号安全动作完成后登记对应通知。 */
@Service
public class AccountSecurityService {

    private final Notifications notifications;

    public AccountSecurityService(@NotNull Notifications notifications) {
        this.notifications = Objects.requireNonNull(notifications, "Notifications 不能为空");
    }

    /** 保存改密结果，并在同一事务中登记安全通知。 */
    @Transactional
    public void passwordChanged(long accountId, @NotNull Instant changedAt) {
        // 此处先保存具体应用负责的密码修改结果。
        notifications.enqueue(PasswordChanged.TYPE, new PasswordChanged(accountId, changedAt));
    }
}
```

`enqueue` 要求活跃事务，返回的 `MessageId` 只证明 Outbox 已登记。需要调用线程立即知道渠道成功或失败，且可以接受外部调用延长当前处理时间时，才使用 `sendNow`。`sendNow` 不经过 Outbox；调用失败直接返回给调用方。

### 保存接收人的语言、区域和渠道偏好

具体应用通过 `NotificationLocaleResolver` 和 `NotificationRegionResolver` 把自己的账号设置转换为 `Locale` 和 `Region`。业务 Service 不直接读取 `Settings`。Sca4d Server 提供领域化接口 `GET /api/me/notification-settings`，返回全局语言、业务区域、渠道覆盖值、通知类型定义和三个独立 Version。

修改单项设置时，把对应 Version 放入 `If-Match`。例如关闭改密通知的可选 Email 渠道：

```http
PUT /api/me/notification-settings/channels
If-Match: "<channelsVersion>"
Content-Type: application/json

{
  "value": {
    "security.password-changed": {
      "email": false,
      "inbox": true
    }
  }
}
```

`PUT /api/me/notification-settings/language` 接受 `{"value":"en-US"}`，`PUT /api/me/notification-settings/region` 接受 `{"value":"US"}`。渠道请求是完整替换，只能包含已注册通知类型的可选渠道；把 `sms` 这类必发渠道写入偏好会被拒绝。

### 模板表达

所有渠道读取同一张 `notification_template`。模板保存平台无关的 Tiptap/ProseMirror JSON；Email 读取 HTML 与纯文本，Inbox 保存渲染快照，自定义渠道可以选择具体应用安装的渲染器。Provider 模板编码只属于 SMS 传输绑定，不是另一份通知模板。

## 通知模板

`notification_template` 为每个 `NotificationType` 保存一份平台无关的多语言模板。`content` 是 Locale 到 `title`、`body` 两个 ProseMirror `doc` 的 JSON object；默认 Locale 必须存在。

常用节点包括段落、标题、引用、列表、代码块、分隔线、图片、表格、文本和换行。`notificationVariable` 是结构化变量节点，`attrs.name` 必须由对应 `NotificationType` 声明。模板公共校验只检查 ProseMirror 结构、变量名称、节点数量和嵌套深度，不限制具体应用增加节点或 Mark。

`NotificationTemplateRenderers` 内置 `text` 和 `html`。`html` 对文本与变量执行 HTML 转义，并在最终输出执行 allowlist 清洗。具体应用可以注册 `NotificationTemplateRenderer` Bean 增加新编码，或使用相同编码替换内置实现；多个自定义实现不能声明同一编码。

Locale 选择顺序是精确标签、应用支持列表中的同语言项、应用默认 Locale。默认组合同时得到无换行标题、纯文本正文和清洗后的 HTML 正文。未知节点由默认渲染器继续处理其子节点，具体应用渲染器可以解释自己的扩展节点。

### 扩展模板渲染器

`NotificationTemplateRenderer` 接收已经完成公共结构校验、Locale 选择后的 `title`、`body` 和变量 Map。具体应用可以注册新的输出格式，也可以用相同编码替换内置 `text` 或 `html`。下面的最小 Markdown 渲染器处理文本、变量和普通容器节点：

```java
/** 把统一通知模板转换为具体应用需要的 Markdown 文本。 */
@Component
public final class MarkdownNotificationTemplateRenderer
        implements NotificationTemplateRenderer {

    /** @return 具体渠道选择此渲染器时使用的稳定编码 */
    @Override
    public @NotNull String code() {
        return "markdown";
    }

    /** 把标题和正文转换成 Markdown 文本。 */
    @Override
    public @NotNull RenderedTemplate render(
            @NotNull JsonNode title,
            @NotNull JsonNode body,
            @NotNull Map<@NotNull String, @NotNull String> variables) {
        String renderedTitle = renderNode(title, variables)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .strip();
        return new RenderedTemplate(renderedTitle, renderNode(body, variables).strip());
    }

    /** 解释当前应用需要支持的 ProseMirror 节点。 */
    private @NotNull String renderNode(
            @NotNull JsonNode node,
            @NotNull Map<String, String> variables) {
        String type = node.path("type").asText();
        if ("text".equals(type)) {
            return node.path("text").asText();
        }
        if ("notificationVariable".equals(type)) {
            String name = node.path("attrs").path("name").asText();
            return Objects.requireNonNull(variables.get(name), "缺少通知变量: " + name);
        }
        StringBuilder result = new StringBuilder();
        node.path("content").forEach(child -> result.append(renderNode(child, variables)));
        if (Set.of("paragraph", "heading", "blockquote", "listItem").contains(type)) {
            result.append('\n');
        }
        return result.toString();
    }
}
```

渲染器编码必须使用小写字母、数字和连字符，并以小写字母开头。两个自定义 Bean 声明相同编码时，应用启动失败。覆盖 `html` 时，自定义实现负责自己的转义和清洗规则；公共模板校验不会为自定义输出增加节点、Mark 或 URL 白名单。

## Notification Email 模块

`modules/notification/sca4dj-notification-email` 把标准通知转换成收件邮箱，并使用 SMTP 投递。模块只保存 SMTP 配置；具体应用实现 `EmailRecipientResolver`，统一模板由 Notification 模块保存和渲染。

```mermaid
flowchart LR
    Notification[Notification]
    Resolver[EmailRecipientResolver]
    Template[(notification_template)]
    Sender[(notification_email_sender)]
    Renderer[NotificationTemplateRenderers]
    Transport[SmtpEmailTransport]
    Server[SMTP Server]

    Notification --> Resolver
    Notification --> Template --> Renderer
    Sender --> Transport
    Resolver --> Transport
    Renderer --> Transport --> Server
```

Email 渠道只有在接收人可以解析、存在启用 SMTP 配置且通知编码存在启用模板时才报告可用。数据库保证每个通知编码最多一张未删除模板，并保证最多一个启用 SMTP 配置；创建的配置和模板默认停用。

Email 使用统一模板的标题作为主题，并同时提交纯文本和安全 HTML 正文。主题不允许换行且不能超过 998 个字符。完整模板限制见[通知模板](#通知模板)。

SMTP 支持 `NONE`、`STARTTLS` 和 `TLS`。密码由 [SecretKeyring](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/security/SecretKeyring.html) 加密后保存，管理读取结果不包含密码或密文。发送前按密文 keyId 选择密钥；密钥缺失、连接失败或 SMTP 拒绝都会使当前投递失败。

模块不保存通知已读状态，不保存用户收件箱，也不拥有另一份 Email 模板。可靠请求、Consumer 失败和 DLQ 由 Notification 与 Messaging 模块负责。

## 站内信

`modules/notification/sca4dj-notification-inbox` 为 Account 接收人保存统一模板的渲染快照。每条记录包含原始 MessageId、Account、通知编码、语言、标题、纯文本、安全 HTML、创建时间、已读时间和 Version。

`notification_inbox_message` 以原始 MessageId 和 Account 建立唯一约束，同一渠道消息重复投递不会生成第二条站内信。列表使用 `beforeId` 游标向前分页；单条已读使用 Version，全部已读只修改仍处于未读状态的记录。

新增站内信或已读状态变化后，模块在事务提交后发布 `notification.inbox.changed@1`。事件只提示 Account 的在线客户端重新读取列表和未读数，不携带正文，也不能替代持久化记录。

Sca4d Server Admin 在顶部铃铛显示最近站内信和未读提示，收到 Realtime 事件后重新读取 HTTP。完整列表位于 `/notifications`，展开未读消息时按 Version 标记已读，也可以一次标记全部已读。客户端不提供删除操作，已渲染快照由服务端持久保存。

HTTP 客户端使用 `GET /api/me/notifications?limit=20` 读取第一页；下一页把当前页最后一项的 `id` 作为 `beforeId`。响应中的 `messages` 按 `id` 倒序排列，`unreadCount` 是当前 Account 的完整未读数量。标记单条已读时提交该消息的 Version：

```http
POST /api/me/notifications/{messageId}/read
Content-Type: application/json

{"version":0}
```

Version 已变化或消息不属于当前 Account 时，请求失败。`POST /api/me/notifications/read-all` 只修改仍未读的记录，不需要客户端提交 Version。

## SMS 通知

`modules/notification/sca4dj-notification-sms` 解析接收人的规范 E.164 手机号，选择 Provider 与传输绑定，并记录 Provider API 的提交结果。`sca4dj-notification-sms-aliyun` 提供阿里云 `SendSms` 适配器。

SMS 公共模块不定义“中国国内”或“国外”分区。具体应用实现 `SmsRouteResolver`，直接使用通知编码、业务 `Region`、`ParsedPhoneNumber` 和 Locale 选择自己声明的 `SmsRoute`。Sca4d Server 示例声明 `china-mainland` 与 `international`，其他应用可以声明不同分区。

Provider 数据库存储类型、`credentialRef`、优先级和启用状态。启用的传输绑定把通知编码、`SmsRoute`、Locale、签名、Provider 模板编码和参数名映射关联起来。Provider 模板编码只是供应商传输约束，业务正文仍来自统一通知模板。

Sca4d Server 使用 `system:sms:read` 和 `system:sms:manage` 管理这些资源。Provider 和绑定创建后默认停用；启用 Provider 前至少需要一个启用绑定。启用中的 Provider 或绑定不能修改或删除。Provider 详情、绑定详情、替换、状态修改和删除使用各自 Version 的强 ETag。管理接口只保存 `credentialRef`，不会接收或返回 AccessKey。

默认 `PrioritySmsProviderSelector` 按数据库优先级尝试当前路由的候选 Provider；具体应用可以替换 `SmsProviderSelector`。Provider 明确拒绝时可以尝试下一个；超时或未知结果不会自动切换，避免同一验证码重复发送。`ACCEPTED` 只表示 Provider API 接受请求，不表示手机最终收到短信。

阿里云 AccessKey、Endpoint 和 Region ID 来自 `sca4dj.notification.sms.aliyun.credentials.<credentialRef>` 部署配置，不保存到 Settings 或数据库。

管理端“短信通知”页面先维护 Provider，再为选中的 Provider 维护通知编码、应用路由、Locale、签名、供应商模板编码及“供应商参数名 → 统一通知变量名”映射。参数映射中的变量必须由对应 `NotificationType` 声明。

### 配置一个阿里云 SMS Provider

先在部署配置中声明凭据引用。凭据值来自环境变量，不写入 Git：

```yaml
sca4dj:
  notification:
    sms:
      aliyun:
        credentials:
          primary:
            access-key-id: ${ALIYUN_SMS_ACCESS_KEY_ID}
            access-key-secret: ${ALIYUN_SMS_ACCESS_KEY_SECRET}
            endpoint: dysmsapi.aliyuncs.com
            region-id: cn-hangzhou
```

然后通过 `POST /api/system/sms/providers` 创建默认停用的 Provider：

```json
{
  "code": "aliyun-primary",
  "type": "ALIYUN",
  "credentialRef": "primary",
  "priority": 10
}
```

为 Provider 创建与应用路由、Locale 和供应商审核模板匹配的绑定。Sca4d Server 的 `china-mainland` 和 `international` 是具体应用声明的 `SmsRoute`，不是 SMS 公共模块的固定分区：

```json
{
  "notificationCode": "authentication.sms-code",
  "route": "china-mainland",
  "languageTag": "zh-CN",
  "signName": "示例应用",
  "providerTemplateCode": "SMS_123456789",
  "parameterMapping": {
    "code": "code",
    "minutes": "expiresInMinutes"
  }
}
```

绑定创建后先读取详情取得 `ETag`，调用 `PATCH /api/system/sms/providers/{providerId}/bindings/{bindingId}/status` 启用绑定；再读取 Provider 详情并调用 `PATCH /api/system/sms/providers/{providerId}/status` 启用 Provider。启用 Provider 前必须至少有一个启用绑定。Provider 或绑定启用后不能直接替换或删除，需要先按当前 `ETag` 停用。

同一通知编码、`SmsRoute` 和 Locale 可以配置多个 Provider。默认选择器按 `priority` 从小到大尝试候选项；明确拒绝可以尝试下一项，超时或未知结果不会切换。供应商模板中的参数名必须完整映射到 `NotificationType` 已声明变量，`credentialRef` 必须与部署配置中的引用一致。
