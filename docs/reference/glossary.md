# 错误码与术语

## 错误码目录

| 范围 | 枚举 | 用途 |
| --- | --- | --- |
| 0-1999 | `CommonErrorCode` | 通用 HTTP、并发、幂等和外部服务错误 |
| 2000-2099 | `AuthenticationErrorCode` | 登录错误 |
| 2100-2199 | `IamErrorCode` | Subject、Account、Credential 和 Principal 错误 |
| 3000-3099 | `NotificationErrorCode` | 通知错误 |
| 3100-3199 | `ImErrorCode` | 即时通信错误 |
| 3200-3299 | `FileErrorCode` | 文件上传、授权和存储错误 |
| 3300-3399 | `ScheduleErrorCode` | Schedule 和执行错误 |
| 3400-3499 | `SystemErrorCode` | 系统字典、菜单和操作日志错误 |
| 3500-3599 | `RoleErrorCode` | 角色定义、分配和并发修改错误 |
| 3600-3699 | `TenantErrorCode` | 租户、成员和租户访问错误 |

### 通用与认证

| 代码 | 枚举 | message key |
| ---: | --- | --- |
| 0 | `SUCCESS` | `error.common.success` |
| 1000-1008 | `BAD_REQUEST` 至 `TOO_MANY_REQUESTS` | `error.common.*` |
| 1009 | `CONCURRENCY_CONFLICT` | `error.common.concurrency-conflict` |
| 1010 | `LOCK_BUSY` | `error.common.lock-busy` |
| 1011 | `IDEMPOTENCY_KEY_REQUIRED` | `error.common.idempotency-key-required` |
| 1012 | `IDEMPOTENCY_IN_PROGRESS` | `error.common.idempotency-in-progress` |
| 1013 | `IDEMPOTENCY_KEY_CONFLICT` | `error.common.idempotency-key-conflict` |
| 1097 | `EXTERNAL_SERVICE_ERROR` | `error.common.external-service` |
| 1098 | `INTERNAL_SERVICE_ERROR` | `error.common.internal-service` |
| 1099 | `SERVER_ERROR` | `error.common.server` |
| 2000 | `BAD_CREDENTIAL` | `error.authentication.bad-credential` |
| 2001 | `SESSION_NOT_FOUND` | `error.authentication.session-not-found` |

### IAM

| 代码 | 枚举 | message key |
| ---: | --- | --- |
| 2100 | `SUBJECT_NOT_FOUND` | `error.iam.subject-not-found` |
| 2101 | `CREDENTIAL_ALREADY_BOUND` | `error.iam.credential-already-bound` |
| 2102 | `CREDENTIAL_NOT_BOUND` | `error.iam.credential-not-bound` |
| 2103 | `PRINCIPAL_NOT_FOUND` | `error.iam.principal-not-found` |
| 2104 | `PRINCIPAL_UNAVAILABLE` | `error.iam.principal-unavailable` |
| 2112 | `PRINCIPAL_EXISTS` | `error.iam.principal-exists` |
| 2114-2119 | `CREDENTIAL_*` | 凭证输入、类型和不存在错误 |
| 2124 | `ACCOUNT_NOT_FOUND` | `error.iam.account-not-found` |
| 2120、2122-2123 | Principal 选择错误 | PLATFORM Principal 选择错误 |
| 2145-2146、2149、2155 | `*_CHANGED` | IAM 并发修改冲突 |

### 角色与租户

| 代码 | 枚举 | message key |
| ---: | --- | --- |
| 3500-3513 | `RoleErrorCode` | `error.role.*` |
| 3600-3615 | `TenantErrorCode` | `error.tenant.*` |

### 通知、即时通信与文件

| 代码 | 枚举 | message key |
| ---: | --- | --- |
| 3000 | `NOTIFICATION_NOT_FOUND` | `error.notification.not-found` |
| 3001-3006 | `NOTIFICATION_*` | 通知内容、发送方和接收方错误 |
| 3100 | `CONVERSATION_NOT_FOUND` | `error.im.conversation-not-found` |
| 3101 | `MESSAGE_NOT_FOUND` | `error.im.message-not-found` |
| 3102 | `NOT_CONVERSATION_MEMBER` | `error.im.not-conversation-member` |
| 3103 | `MESSAGE_REVOKE_FORBIDDEN` | `error.im.message-revoke-forbidden` |
| 3104-3114 | IM 输入和工作空间错误 | 会话、消息、成员和图片附件错误 |
| 3200 | `FILE_NOT_FOUND` | `error.file.not-found` |
| 3201 | `FILE_FORBIDDEN` | `error.file.forbidden` |
| 3202 | `FILE_STATUS_INVALID` | `error.file.status-invalid` |
| 3203 | `FILE_UPLOAD_INCOMPLETE` | `error.file.upload-incomplete` |
| 3204 | `FILE_TOO_LARGE` | `error.file.too-large` |
| 3205 | `FILE_SIGNATURE_SCENE_UNKNOWN` | `error.file.signature-scene-unknown` |
| 3206 | `FILE_SIGNATURE_BATCH_INVALID` | `error.file.signature-batch-invalid` |
| 3207 | `FILE_UPLOAD_GRANT_INVALID` | `error.file.upload-grant-invalid` |
| 3208 | `FILE_ALREADY_CONSUMED` | `error.file.already-consumed` |
| 3209 | `FILE_DISABLED` | `error.file.disabled` |
| 3210 | `FILE_FILENAME_INVALID` | `error.file.filename-invalid` |
| 3211-3215 | `FILE_LOCAL_*` | 本地文件存储错误 |
| 3216-3223 | `FILE_MINIO_*` | MinIO 存储错误 |
| 3224-3229 | 文件签名和上传校验错误 | requestId、READ、元数据、大小和有效期错误 |
| 3298 | `FILE_STORAGE_ERROR` | `error.file.storage` |

### 调度

| 代码 | 枚举 | message key |
| ---: | --- | --- |
| 3300 | `SCHEDULE_NOT_FOUND` | `error.schedule.not-found` |
| 3301 | `SCHEDULE_INVALID` | `error.schedule.invalid` |
| 3302 | `SCHEDULE_TERMINAL` | `error.schedule.terminal` |
| 3303 | `SCHEDULE_TASK_TYPE_IMMUTABLE` | `error.schedule.task-type-immutable` |
| 3304 | `SCHEDULE_VERSION_CHANGED` | `error.schedule.version-changed` |
| 3305 | `SCHEDULE_IF_MATCH_REQUIRED` | `error.schedule.if-match-required` |
| 3306 | `SCHEDULE_TASK_TYPE_UNKNOWN` | `error.schedule.task-type-unknown` |
| 3307 | `SCHEDULE_PAYLOAD_VERSION_UNKNOWN` | `error.schedule.payload-version-unknown` |
| 3308 | `SCHEDULE_EXECUTION_NOT_FOUND` | `error.schedule.execution-not-found` |
| 3309 | `SCHEDULE_EXECUTION_NOT_ALLOWED` | `error.schedule.execution-not-allowed` |
| 3310 | `SCHEDULE_SYNCHRONIZATION_FAILED` | `error.schedule.synchronization-failed` |

### 系统管理

| 代码 | 枚举 | message key 范围 |
| ---: | --- | --- |
| 3400 | `AUDIT_LOG_NOT_FOUND` | `error.audit.log-not-found` |
| 3401-3420 | `DICTIONARY_*` | `error.dictionary.*` |
| 3421-3428 | `MENU_*` | `error.menu.*` |
| 3429-3432 | `DICTIONARY_*_CHANGED` | 字典及字典项内容、排序并发修改冲突 |
| 3433 | `MENU_CHANGED` | `error.menu.changed` |

运行时 `ErrorCodeCatalog` 提供代码、message key 和定义来源。失败响应同时包含 code、messageKey、messageArgs、message 和 traceId。

## Subject

自然人或系统的稳定认证主体。Subject 拥有生命周期，但不直接拥有登录标识和认证材料。

## Account

Subject 在一个认证作用域内的登录账号。Account 直接拥有登录标识、Credential 和 Session；一个 Subject 可以拥有多个 Account。

## Credential

Account 用于证明身份的认证材料，如密码。凭据类型由 Scheme 负责绑定和验证。

## Authentication

认证能力和当前入口的登录态操作，不是业务 Principal 的同义词。

## Session

一次 token 登录会话及其中保存的登录信息。当前 Principal 不以可变值存入 Session。

## Principal

本次业务请求代表的执行主体。一个 Subject 可以拥有多个不同类型或租户的 Principal。

## PrincipalSelection

根据请求输入和 Subject 可用主体，在本请求内选择 Principal 的过程。

## Permission

执行一类功能动作的明确编码。角色聚合 Permission；Permission 不包含具体资源范围。

## SessionPermission

当前 Principal 的角色和功能权限快照，供 Sa-Token RBAC 使用。

## AccessCriteria

Policy 为当前请求生成、Repository 可直接用于 SQL 的领域资源范围。

## CurrentRequest

HTTP、STOMP、SYSTEM 共用的请求信息容器，保存 traceId、认证结果和资源访问条件。

## 业务事件

事务提交后发布的业务变化，例如订单已创建。它可以通过 MessagePublisher 发送；实时事件只通知页面重新查询数据。

## View

HTTP 输出对象，由 Controller 从 Service 返回结果构造。View 不查询数据库，也不作为 Service 返回类型。

## Request

HTTP 输入对象，负责形状和基础校验。业务状态与跨记录约束由 Service 判断。

## Repository

业务包中的数据库访问类，负责调用 Mapper、转换查询结果并应用 AccessCriteria。它不是通用 CRUD Manager。

## Entity

数据库表映射对象，可以由 Service 和持久化流程直接复用，但不能直接作为 HTTP 响应或 View 字段类型。只有形成独立业务概念时才建立语义明确的业务类型，不按 Entity 字段机械复制，也不建立泛化 `model` 包。

## Outbox

与业务事务共同保存的待发布消息记录，由后台 dispatcher 投递。

## ClientState

服务端通过响应头提示客户端刷新 `/me` 或资源快照的协议，不改变授权结果。
