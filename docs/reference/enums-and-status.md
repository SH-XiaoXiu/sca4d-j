# 枚举与状态

实现 `BaseEnum` 的枚举通过 `EnumValueCodec`、Jackson 和 `BaseEnumTypeHandler` 使用相同字符串值。数据库和 JSON 都不使用 Java ordinal。

## 身份、租户和权限

| Java 类型 | 值 | 说明 |
| --- | --- | --- |
| `PrincipalType` | `SYSTEM`、`PLATFORM`、`TENANT`、`DEFAULT` | 系统任务、平台、租户成员和默认业务主体 |
| `CredentialType` | `PASSWORD`、`TOTP` | 密码或身份验证器凭据 |
| `SubjectStatus` | `ACTIVE`、`DISABLED` | Subject 状态 |
| `AccountStatus` | `ACTIVE`、`DISABLED` | Account 状态 |
| `AccountScopeType` | `GLOBAL`、`TENANT` | Account 登录标识作用域 |
| `PrincipalStatus` | `ACTIVE`、`DISABLED` | 执行主体状态 |
| `CredentialStatus` | `ACTIVE`、`DISABLED` | 凭据状态 |
| `RoleStatus` | `ACTIVE`、`DISABLED` | 角色状态 |
| `TenantStatus` | `ACTIVE`、`DISABLED` | 租户状态 |
| `TenantType` | `DEFAULT` | 标准租户类型 |
| `PermissionType` | `MODULE`、`RESOURCE`、`ACTION` | 权限目录节点类型 |
| `AccessAction` | `READ`、`WRITE`、`MANAGE` | 数据访问操作，级别依次升高 |
| `IdentityProviderProtocol` | `OIDC`、`OAUTH2`、`LDAP` | 外部身份源协议；创建 Provider 后不可修改 |
| `IdentityProviderStatus` | `ACTIVE`、`DISABLED` | 是否允许出现在认证入口并创建 Proof |
| `IdentityProviderRegistrationPolicy` | `DISABLED`、`JIT` | 未关联 external subject 是否允许即时注册 |

停用 Credential 只禁止该认证材料登录；停用 Account 会结束并禁止该 Account 的登录；停用 Principal 只禁止选择该执行主体；停用 Subject 禁止其全部 Account 登录。四者不能互相替代。

`DEFAULT` 和 `PLATFORM` Principal 的 `tenantId` 为空；`TENANT` Principal 必须包含 `tenantId`；`SYSTEM` 只由后台任务创建。

`LIMITED` 使用空集合时表示不能访问任何数据，不表示 `ALL`。

## 并发控制

| Java 类型 | 值 | 说明 |
| --- | --- | --- |
| `IdempotencyStatus` | `PROCESSING`、`SUCCEEDED` | Redis 中保存的请求状态 |
| `IdempotencyBeginStatus` | `ACQUIRED`、`REPLAY`、`IN_PROGRESS`、`CONFLICT` | 请求进入幂等切面时的判断结果 |
| `RateLimitType` | `GLOBAL`、`IP`、`USER`、`UA`、`CONNECTION`、`CUSTOM` | 限流 key 的组成方式 |

`ACQUIRED` 表示当前请求可以执行，`REPLAY` 返回已经保存的成功响应，`IN_PROGRESS` 返回稍后重试，`CONFLICT` 表示同一个 key 对应了不同请求内容。

## 消息和 Outbox

| Java 类型 | 值 | 说明 |
| --- | --- | --- |
| `MessagePublishMode` | `DIRECT`、`AFTER_COMMIT`、`OUTBOX` | 消息发送时间 |
| `MessageOutboxStatus` | `PENDING`、`SENDING`、`SENT`、`FAILED`、`DEAD` | Outbox 投递状态 |

| 发布模式 | 数据库回滚 | 进程在发送前退出 |
| --- | --- | --- |
| `DIRECT` | 消息可能已经发送 | 已完成调用的消息通常已发送 |
| `AFTER_COMMIT` | 不发送 | 提交后、回调前退出可能丢失 |
| `OUTBOX` | Outbox 记录一起回滚 | 其他实例继续发送已提交记录 |

`SENT` 表示 RabbitMQ 已确认接收，不表示消费者已经完成业务处理。`FAILED` 可以重试，超过最大次数后改为 `DEAD`。

## 实时通信

| Java 类型 | 值 | 说明 |
| --- | --- | --- |
| `RealtimeTargetType` | `SUBJECT`、`PRINCIPAL`、`TENANT`、`TOPIC`、`CONNECTION` | 接收对象 |
| `RealtimeTransport` | `WEBSOCKET`、`SSE` | 传输方式 |

`SUBJECT` 发送给同一登录身份的连接；`PRINCIPAL` 发送给指定执行主体；`TENANT` 发送给租户内连接；`TOPIC` 发送给订阅业务主题的连接；`CONNECTION` 只发送给一个连接。

## 文件

| Java 类型 | 值 | 说明 |
| --- | --- | --- |
| `FileStorageProvider` | `LOCAL`、`MINIO` | 服务端存储实现 |
| `FileUploadMode` | `DIRECT`、`PROXY` | 客户端直传或通过应用上传 |
| `FileUploadMethod` | `PUT` | 上传 HTTP Method |
| `FileSignatureOperation` | `UPLOAD`、`READ` | 申请上传票据或读取地址 |
| `FileStatus` | `PENDING`、`READY`、`IN_USE`、`DELETE_PENDING`、`DELETING`、`DELETE_FAILED`、`DELETED`、`EXPIRED` | 文件状态 |

```text
PENDING → READY → IN_USE
PENDING/READY → EXPIRED
IN_USE → DELETE_PENDING → DELETING → DELETED
                                   ↘ DELETE_FAILED → DELETING
```

`READY` 表示上传已完成但还没有写入业务字段。`IN_USE` 表示业务记录已经使用该 fileId。

## 通知和即时通信

| Java 类型 | 值 | 说明 |
| --- | --- | --- |
| `NotificationCategory` | `SYSTEM` | 系统通知 |
| `NotificationDeliveryStatus` | `UNREAD`、`READ` | 每个接收人的阅读状态 |
| `ImConversationType` | `SINGLE`、`GROUP` | 单聊或群聊 |
| `ImMemberRole` | `OWNER`、`MEMBER` | 会话内角色 |
| `ImMessageContentType` | `TEXT`、`IMAGE` | 文本或图片消息 |
| `ImMessageStatus` | `NORMAL`、`REVOKED` | 正常或已撤回 |

通知阅读状态保存在接收人记录中。`ImMemberRole` 只用于 IM 会话，不参与 IAM 角色权限计算。

## 字典、菜单和 DEFAULT Profile

| Java 类型 | 值 | 说明 |
| --- | --- | --- |
| `DictionaryKind` | `CODE_ENUM`、`BUSINESS_CODE`、`INPUT_SUGGESTION` | 代码枚举、业务编码和输入建议 |
| `DictionarySource` | `CODE`、`CONFIGURATION` | 代码或配置维护来源 |
| `DictionaryStatus` | `ACTIVE`、`DISABLED` | 管理字典状态 |
| `DictionaryTone` | `DEFAULT`、`INFO`、`SUCCESS`、`WARNING`、`DANGER` | 前端状态颜色类别 |
| `ResourceNavigationState` | `NAVIGABLE`、`FORBIDDEN`、`DELETED`、`UNSUPPORTED` | 资源链接状态 |
菜单图标使用经过格式校验的 Lucide 导出名称字符串，例如 `LayoutDashboard`、`Users` 和 `FolderTree`。图标目录是开放集合，不再由 Java 枚举限定。

## 审计和运行环境

| Java 类型 | 值 | 说明 |
| --- | --- | --- |
| `AuditMode` | `DEFAULT`、`ASYNC`、`SYNC` | 使用全局配置、异步独立写入、同步事务写入 |
| `AuditResult` | `SUCCESS`、`FAILED` | 操作结果 |
| `BootEnvironment` | `LOCAL`、`DEV`、`TEST`、`STAGING`、`PROD` | 当前运行环境 |
| `DependencyCriticality` | `REQUIRED`、`DEGRADED`、`OPTIONAL` | 外部服务失败对启动和 readiness 的影响 |

## 调度

| Java 类型 | 值 | 说明 |
| --- | --- | --- |
| `ScheduleType` | `ONCE`、`CRON` | 一次性计划和 Cron 计划 |
| `ScheduleStatus` | `ACTIVE`、`PAUSED`、`COMPLETED`、`FAILED`、`CANCELLED` | Schedule 状态；`CANCELLED` 是数据库内部删除状态 |
| `ExecutionStatus` | `PENDING`、`RUNNING`、`SUCCESS`、`FAILED`、`SKIPPED` | 单次执行状态 |
| `TriggerSource` | `SCHEDULED`、`MANUAL`、`RECOVERY` | 计划触发、人工触发和故障恢复 |
| `SystemJobResult` | `SUCCESS`、`FAILED` | 系统 Job 最近一次执行结果 |
| `SystemJobScheduleType` | `INTERVAL`、`CRON` | 系统 Job 的固定间隔或 Cron 计划 |
| `SystemJobTriggerState` | `NONE`、`NORMAL`、`PAUSED`、`COMPLETE`、`ERROR`、`BLOCKED` | 管理端显示的 Quartz Trigger 状态 |

## 通用枚举

| Java 类型 | 值 | 说明 |
| --- | --- | --- |
| `EnabledStatus` | `ENABLED`、`DISABLED` | 通用启停状态 |
| `DeletedStatus` | `NORMAL`、`DELETED` | 通用删除状态 |
| `SortDirection` | `ASC`、`DESC` | 排序方向 |
| `BizIdMode` | `NUM`、`CODE`、`NAMESPACED_CODE` | 数字 ID、业务编码、带命名空间的编码 |
| `SupportedLocale` | `ZH_CN`、`EN_US` | `zh-CN` 和 `en-US` |

## HTTP 响应分类

| `ResponseCode` | 分类编码 | HTTP | 使用情况 |
| --- | ---: | ---: | --- |
| `SUCCESS` | 0 | 200 | 成功 |
| `BAD_REQUEST` | 1000 | 400 | 请求参数错误 |
| `UNAUTHORIZED` | 1001 | 401 | 未登录或 Session 失效 |
| `FORBIDDEN` | 1002 | 403 | 权限或数据访问检查失败 |
| `NOT_FOUND` | 1003 | 404 | 资源不存在 |
| `METHOD_NOT_ALLOWED` | 1004 | 405 | HTTP Method 不支持 |
| `CONFLICT` | 1005 | 409 | 版本、幂等或状态冲突 |
| `PAYLOAD_TOO_LARGE` | 1006 | 413 | 请求内容过大 |
| `UNSUPPORTED_MEDIA_TYPE` | 1007 | 415 | Content-Type 不支持 |
| `TOO_MANY_REQUESTS` | 1008 | 429 | 请求被限流 |
| `PRECONDITION_FAILED` | 1009 | 412 | `If-Match` 与当前版本不一致 |
| `PRECONDITION_REQUIRED` | 1010 | 428 | 缺少服务端要求的 `If-Match` |
| `EXTERNAL_SERVICE_ERROR` | 1097 | 500 | 外部服务失败 |
| `INTERNAL_SERVICE_ERROR` | 1098 | 500 | 已识别的内部错误 |
| `SERVER_ERROR` | 1099 | 500 | 未分类服务端错误 |

`ResponseCode` 的分类编码不是业务失败响应中的最终 code。`BizErrorCode` 提供最终 code，例如 `SCHEDULE_VERSION_CHANGED` 返回 3304，并使用 `PRECONDITION_FAILED` 分类产生 HTTP 412。
