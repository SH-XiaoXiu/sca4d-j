# 阿里云短信适配器

阿里云短信适配器实现 `SmsProviderClient`，通过阿里云 V2 Java SDK 提交短信请求，并查询发送明细。它把 SDK 的响应转换为 `SmsProviderResult`，由[短信通知渠道](/capabilities/messaging-notification/sms)决定确认、重试或切换候选 Provider。适合配置阿里云短信 Provider、准备模板与签名的开发者阅读。

**根构建当前未包含该模块，使用前需在构建中引入。**

依据是根目录 `settings.gradle.kts` 的 `include` 列表。该列表包含 `modules:notification:sca4dj-notification-sms`，但没有 `modules:notification:sca4dj-notification-sms-aliyun`。模块目录下存在 `build.gradle.kts`。引入后，该模块可以参与构建。

本页描述模块源码的行为。它不表示任何具体应用已经使用该适配器。

## 概念

`AliyunSmsProviderClient.TYPE` 的值为 `ALIYUN`。`notification_sms_provider` 中 `provider_type` 为 `ALIYUN` 的记录由该客户端投递。

投递一条短信时，客户端使用三个来源的信息：

- `SmsProvider.credentialRef()`：选择部署配置中的凭据。
- `SmsProviderBinding`：提供 `signName()`（短信签名）和 `providerTemplateCode()`（阿里云模板编码）。
- 参数表：由 `SmsNotificationDelivery` 按绑定的 `parameterMapping` 从通知变量中取出。客户端把参数表序列化为 JSON，作为 `TemplateParam`。

例如，绑定的 `parameterMapping` 为 `{"code": "verificationCode"}`，通知变量 `verificationCode` 的值为 `123456`。此时提交的 `TemplateParam` 为 `{"code":"123456"}`。上述映射由具体应用的绑定配置决定。

目标手机号的处理如下：

- 地区为 `CN`（中国大陆）时，去掉 E.164 前缀 `+86` 后提交。
- 其他地区时，提交完整的 E.164 值。

## 核心对象

| 对象 | 负责 | 不负责 |
|---|---|---|
| `AliyunSmsProviderClient` | 实现 `send` 与 `query`，按凭据引用创建并缓存 SDK `Client`，把响应映射为 `SmsProviderResult` | 选择 Provider，决定重试 |
| `AliyunSmsProperties` | 绑定 `sca4dj.notification.sms.aliyun.credentials` 下的凭据 | 保存数据库中的 Provider 配置 |
| `AliyunSmsProperties.Credential` | 保存单个凭据引用的 AccessKey、Endpoint 与 Region ID | 校验凭据是否有效 |
| `AliyunSmsAutoConfiguration` | 注册 `SmsProviderClient` Bean，并启用 `AliyunSmsProperties` | 注册 Provider 配置 |
| `NotificationSmsAliyunException` | 表示 SDK 客户端创建失败的技术异常 | 向调用方传播。`send` 与 `query` 捕获 `Exception`，调用方不直接收到该异常 |

`AliyunSmsAutoConfiguration` 的顺序位于 `SmsNotificationAutoConfiguration` 之后。自动配置的导入文件为 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。文件内容为 `com.sca4d.notification.sms.aliyun.config.AliyunSmsAutoConfiguration`。

## 提交与响应映射

`send` 的处理如下：

1. 按上文规则计算目标手机号。
2. 构造 `SendSmsRequest`，设置手机号、`SignName`、`TemplateCode` 和 `TemplateParam`。
3. 调用 `sendSmsWithOptions`，并映射响应：

| 条件 | 返回的 `SmsProviderResult` |
|---|---|
| 响应 `Code` 为 `OK`（忽略大小写） | `ACCEPTED`，携带 `RequestId` 与 `BizId` |
| 响应 `Code` 不为 `OK`，或响应体为空 | `REJECTED`，失败码为响应的 `Code`（响应体为空时失败码为空） |
| 调用 SDK 或创建客户端时抛出 `Exception` | `UNKNOWN`，失败码为异常的简单类名 |

`ACCEPTED` 表示阿里云接受了请求，不表示手机已经收到短信。`UNKNOWN` 的结果由短信渠道登记查询，不切换到其他 Provider。

`query` 使用 `BizId` 查询发送明细。查询参数包括 `BizId`、手机号、查询日期、第 1 页与每页 1 条。查询日期为调用时 `LocalDate.now()` 的 `yyyyMMdd` 格式，使用运行 JVM 的默认时区。查询结果映射如下：

| 条件 | 返回的 `SmsProviderResult` |
|---|---|
| `bizId` 为空或只含空白 | `UNKNOWN`，失败码 `SMS_BIZ_ID_MISSING`，不调用 SDK |
| 响应为空，或 `Code` 不为 `OK` | `UNKNOWN`，失败码为响应的 `Code`，响应为空时为 `SMS_QUERY_EMPTY` |
| 响应没有发送明细，或第一条的 `SendStatus` 为空 | `UNKNOWN`，失败码 `SMS_QUERY_PENDING` |
| 第一条的 `SendStatus` 为 `2` | `DELIVERED` |
| 第一条的 `SendStatus` 为 `3` | `FAILED`，失败码为明细的 `ErrCode` |
| 其他 `SendStatus` | `PENDING` |
| 调用 SDK 或创建客户端时抛出 `Exception` | `UNKNOWN`，失败码为异常的简单类名 |

查询只读取第一条发送明细。查询日期使用调用时的日期。因此，跨日发送的短信可能查不到明细，结果保持 `UNKNOWN`。

## 凭据与配置

`AliyunSmsProperties` 的配置前缀为 `sca4dj.notification.sms.aliyun`。`credentials` 是以凭据引用为键的映射。`SmsProvider.credential_ref` 必须与 `credentials` 的键相同。

| 配置键 | 默认值 | 作用 |
|---|---|---|
| `sca4dj.notification.sms.aliyun.credentials.<credentialRef>.access-key-id` | 无 | 阿里云 AccessKey ID |
| `sca4dj.notification.sms.aliyun.credentials.<credentialRef>.access-key-secret` | 无 | 阿里云 AccessKey Secret |
| `sca4dj.notification.sms.aliyun.credentials.<credentialRef>.endpoint` | `dysmsapi.aliyuncs.com` | SDK 的 Endpoint |
| `sca4dj.notification.sms.aliyun.credentials.<credentialRef>.region-id` | `cn-hangzhou` | SDK 的 Region ID |

`<credentialRef>` 是凭据引用名，例如 `primary`。Spring 的属性名写法与 Java 属性 `accessKeyId`、`accessKeySecret`、`endpoint` 和 `regionId` 对应。

凭据在首次使用该引用时创建 SDK `Client`，并以凭据引用为键缓存在 `ConcurrentHashMap` 中。创建时的检查如下：

- 引用在 `credentials` 中不存在时，抛出 `IllegalStateException`，消息为「阿里云 SMS 凭据引用没有部署配置」。该异常在 `send` 与 `query` 中被捕获，结果为 `UNKNOWN`。
- AccessKey ID、AccessKey Secret、Endpoint 或 Region ID 为空白时，抛出 `IllegalStateException`，同样结果为 `UNKNOWN`。
- SDK `Client` 创建失败时，抛出 `NotificationSmsAliyunException`，同样结果为 `UNKNOWN`。

`SmsConfigurationService` 保存 Provider 时不检查 `credential_ref` 是否存在于 `credentials` 中。因此，配置错误在首次投递或查询时才暴露。源码中未定义凭据变更后的刷新方式。缓存的 `Client` 不会因配置变化而重建。

密钥只通过部署配置提供，不写入 `notification_sms_provider`。失败日志的消息包含 Provider 类型与异常的简单类名，并附带异常对象。异常对象的消息文本由 SDK 决定。

## 模板与签名

模板和签名由 `SmsProviderBinding` 提供，并通过 `SmsConfigurationService.createBinding` 或 `replaceBinding` 保存：

- `sign_name` 映射为 SDK 的 `SignName`。
- `provider_template_code` 映射为 SDK 的 `TemplateCode`。
- `parameter_mapping` 的键是阿里云模板中的参数名，值是通知变量名。

绑定的 `language_tag` 与 `route` 决定使用哪一条绑定。客户端不检查签名或模板是否已在阿里云控制台中审核通过。阿里云响应的 `Code` 不为 `OK` 时，客户端映射为 `REJECTED`，失败码为该 `Code`。

## 示例

以下配置声明一个凭据引用 `primary`。环境变量名为示例，实际值由部署环境提供，不写入源码或数据库。

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

以下绑定把通知变量 `verificationCode` 映射到阿里云模板参数 `code`。绑定所属 Provider 的 `credential_ref` 为 `primary`。通知编码须为应用已注册的通知类型。

| 字段 | 值 |
|---|---|
| `notification_code` | `WORKSPACE/MEMBER/INVITATION`（示例编码） |
| `route` | `china-mainland` |
| `language_tag` | `zh-CN` |
| `sign_name` | 在阿里云控制台已审核的签名 |
| `provider_template_code` | 在阿里云控制台已审核的模板编码 |
| `parameter_mapping` | `{"code": "verificationCode"}` |

`parameter_mapping` 的键是阿里云模板参数名，值是通知变量名。通知变量必须由通知类型声明，否则 `createBinding` 返回 `BINDING_INVALID`。

## 扩展方式

阿里云适配器由 `AliyunSmsAutoConfiguration` 注册为 `SmsProviderClient` Bean，短信渠道通过 `List<SmsProviderClient>` 收集它。

其他厂商的适配器按[短信通知渠道](/capabilities/messaging-notification/sms)中的 `SmsProviderClient` 接口实现，并注册为 Bean。Provider 的 `provider_type` 与该客户端的 `providerType()` 相同时，该厂商参与投递。

## 相关页面

- [短信通知渠道](/capabilities/messaging-notification/sms)
- [消息内容与投递结果](/capabilities/messaging-notification/message-content)
- [Notification 模块](/capabilities/messaging-notification/notification)
- [Outbox 派发与清理](/capabilities/messaging-notification/outbox)
