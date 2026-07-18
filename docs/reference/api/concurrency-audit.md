# 并发与审计

## 乐观锁

包：`com.sca4d.common.concurrent`

| 类型 | 成员 | 说明 |
| --- | --- | --- |
| `VersionedRequest` | `getVersion()` | 写 Request 携带当前版本 |
| `ConcurrencyConflictException` | messageKey、refreshTarget、data | 映射为 HTTP 409 和 ClientState Header |

Repository 更新条件必须包含 ID、访问范围、状态和 version，并在同一条 SQL 中增加 version。

## 幂等

包：`com.sca4d.common.concurrent.idempotency`

| 类型 | 主要成员 | 默认实现或状态 |
| --- | --- | --- |
| `IdempotentRequest` | `getIdempotencyKey()` | 创建或动作 Request 实现的接口 |
| `@Idempotent` | operation、ttlSeconds | 方法注解 |
| `IdempotencyAspect` | AOP 入口 | 计算请求指纹并保存响应 |
| `IdempotencyStore` | `begin(...)`、`succeed(...)`、`release(...)` | `RedisIdempotencyStore` |
| `IdempotencyRecord` | operation、key、fingerprint、status、response | Redis 记录 |
| `IdempotencyStatus` | `PROCESSING`、`SUCCEEDED` | Redis 记录状态 |
| `IdempotencyBeginStatus` | `ACQUIRED`、`REPLAY`、`IN_PROGRESS`、`CONFLICT` | `begin(...)` 结果 |
| `IdempotencyKeyRequiredException` | 业务错误 | key 缺失 |
| `IdempotencyInProgressException` | 业务错误 | 首次请求仍在执行 |
| `IdempotencyKeyConflictException` | 业务错误 | 同 key 对应不同请求 |

## 分布式锁

包：`com.sca4d.common.concurrent.lock`

| 类型 | 主要成员 | 默认实现 |
| --- | --- | --- |
| `@DistributedLock` | key、waitTimeSeconds、leaseTimeSeconds | 方法注解 |
| `DistributedLockExecutor` | 执行带锁操作 | `RedissonDistributedLockExecutor` |
| `DistributedLockTemplate` | 动态调用入口 | 委托 Executor |
| `LockedOperation<T>` | `execute()` | 锁内操作 |
| `SpelKeyEvaluator` | 计算注解 key | 幂等、锁和限流共用 |
| `LockBusyException` | 业务错误 | 等待锁超时 |

## 限流

包：`com.sca4d.common.concurrent.ratelimit`

| 类型 | 主要成员 | 默认实现或结果 |
| --- | --- | --- |
| `@RateLimit` | types、capacity、refillTokens、timeWindowSeconds、message | 方法注解 |
| `RateLimitRule` | 类型化限流规则 | 由注解构建 |
| `RateLimitType` | IP、USER、CONNECTION 等维度 | 具体值见枚举参考 |
| `RateLimitKeyGenerator` | 根据 CurrentRequest 生成 key | 拒绝不存在的维度 |
| `RateLimiter` | acquire | `Bucket4jRedisRateLimiter` |
| `RateLimitContext` | 请求和连接信息 | `RateLimitContextHolder` 保存当前值 |
| `RateLimitResult` | allowed、retryAfter | 限流结果 |
| `RateLimitException` | retryAfter | 映射 HTTP 429 |

## 审计目录

包：`com.sca4d.common.audit`

| 类型 | 主要成员 | 说明 |
| --- | --- | --- |
| `@Audit` | module、action、mode、payloadType、payload、summary、targetType、targetId | 方法或类注解 |
| `AuditMode` | `DEFAULT`、`ASYNC`、`SYNC` | 写入方式 |
| `AuditOperationContributor` | `auditOperations()` | 领域贡献操作目录 |
| `AuditOperationDefinition` | `standard(...)` 或完整构造器 | module/action 名称键和排序 |
| `AuditOperationCatalog` | `require(...)`、`operations()` | 聚合并检查重复定义 |

`payload`、`summary`、`targetId` 在业务方法结束后计算，可引用 `#result` 和 `#error`。`summary` 为空时，`SysLogAuditRecorder` 使用操作目录中的 `audit.summary.<module>.<action>` 消息键。

## 审计记录与 Payload

| 类型 | 主要成员 | 说明 |
| --- | --- | --- |
| `AuditSource` | `actor()`、`request()` | 从 `CurrentRequest` 创建默认操作者和请求摘要；默认实现为 `DefaultAuditSource` |
| `AuditActor` | Subject、Account 和 Principal 快照 | 由 AuditSource 提供 |
| `AuditTarget` | resourceType、resourceId | 来自注解的 targetType 和 targetId |
| `AuditRequest` | traceId、入口和请求摘要 | 不保存密码和 token |
| `AuditPayload` | 标记接口 | 领域详情类型 |
| `AuditPayloadCodec<P>` | payloadType、payloadClass、encode、decode | Payload 版本化编解码 |
| `AuditPayloadRegistry` | 按类型查找 Codec | 拒绝重复 payloadType |
| `AuditEnvelope` | 持久化 envelope | `AuditCodec` 编解码 |
| `AuditRecord` | Recorder 输入 | 包含 actor、target、result、payload |
| `AuditRecorder` | `record(...)` | 默认业务实现 `SysLogAuditRecorder`，无数据库实现时为 `LoggingAuditRecorder` |
| `AuditPayloadViewFactory<P>` | 转换管理端安全 View | 由 `AuditPayloadViewRegistry` 聚合 |

业务通过 `@Audit` 的 `summary`、`targetId` 和 `payload` 表达式引用参数、返回值或异常；请求和操作者事实由 `AuditSource` 读取。Service 不应创建新的 ThreadLocal 审计上下文。

审计注解和 Codec 的参数见[接口与注解](/reference/interfaces-annotations.md#审计)。
