# 接口与注解

## 认证与会话接口

### `CredentialScheme<C, P>`

定义一种认证材料的持久化和验证方式。

| 方法 | 说明 |
| --- | --- |
| `credentialType()` | 数据库中的稳定 `CredentialType` |
| `credentialClass()` | 唯一接受的认证材料 Java 类型 |
| `proofClass()` | 唯一接受的 Proof Java 类型 |
| `bind(accountId, credential)` | 为 Account 首次绑定该类认证材料 |
| `replace(accountId, credentialId, version, credential)` | 按 Credential 版本替换已有认证材料 |
| `verify(accountId, proof)` | 验证指定 Account 的 Proof 并返回 Subject、Account 和 Credential 版本快照 |

当前实现包括 `PasswordCredentialScheme` 和 `TotpCredentialScheme`。TOTP Scheme 使用 googleauth 匹配验证码，解密 `iam_totp` 密钥，并通过数据库条件更新单次消费时间步。新增 Scheme 只负责认证，不选择 Principal、不加载权限。

### `TotpEnrollmentService`

`create(...)` 在 STEP_UP 单次消费后创建 Redis enrollment；`qrCode(...)` 只为同一 Subject、Account 和浏览器返回禁止缓存的 PNG；`activate(...)` 与 `replace(...)` 验证首次验证码、用 Redis CAS 消费 enrollment，再写入 TOTP Credential；`delete(...)` 在保留其他第二因素的前提下删除 Credential。替换和删除使用强 `If-Match`。Credential 写入和旧 Session 失效位于同一 Subject 集群锁和数据库事务中；Session 失效失败时数据库写入回滚。

### `Authentication`

适配 token 和会话运行时。当前实现为 `SaTokenAuthentication`。

| 方法 | 说明 |
| --- | --- |
| `createSession(...)` | 使用已认证 Subject、固定 Account、全部 Principal 和权限创建会话 |
| `replaceSession(...)` | 替换指定 Account 的完整 Account-Session 状态 |
| `currentSession()` | 恢复当前请求会话；未登录返回 null |
| `logout()` | 结束当前请求关联会话 |
| `onlineAccountIds()` | 读取当前仍在线的 Account 主键 |
| `sessions(accountId)` | 读取指定 Account 的安全会话快照 |
| `endSession(accountId, sessionId)` | 结束指定 Account 的一个项目会话 |
| `endSessions(accountId)` | 结束指定 Account 的全部终端会话 |

`Authentication` 不验证 Credential、不选择 Principal、不判断权限。

### `RecoveryCodeService`

管理 Account 的恢复码集合。`issue(accountId)` 生成 10 个高熵恢复码，数据库只接收 Argon2id 摘要，明文只允许当前 HTTP 响应展示一次。`revoke(accountId)` 使当前集合失效并删除旧摘要。`findByAccounts(accountIds)` 使用一次批量查询返回签发时间和剩余数量，不返回摘要或明文。`consume(accountId, code)` 固定执行十次 Argon2id 校验，并通过数据库条件更新使同一恢复码只有一个并发请求成功。`requireCurrent(...)` 确认已使用记录仍属于当前集合，且 Account 与 Subject 仍可认证。

恢复码签发和撤销由账号安全 Service 在 STEP_UP、Subject 锁和 Account 第二因子状态事务中调用。`RecoveryCodeAuthMethod` 验证恢复码并返回已使用记录的 ID 和版本；`AuthenticationProcessService` 不生成恢复码，也不依赖恢复码 Repository。

### `AccountRecoveryService`

`replacePassword(authenticationId, browserHash, password)` 单次完成已经验证的 RECOVERY 认证，在 Subject 集群锁内重新读取 Account、Subject 和 PASSWORD Credential，替换密码后结束该 Account 的全部登录会话。密码写入失败时不恢复已经消费的认证或恢复码。

### `Session`

只读会话句柄，提供 sessionId、token、Subject、固定 Account、Principal 列表、权限 Map、设备和时间信息。`getTokenValue()` 只供认证实现和登录响应使用，普通 View 不得返回。`invalidate()` 必须幂等。

### `PrincipalSelection<P>`

`parameterType()` 声明额外选择参数类型；`select(...)` 根据 `CurrentRequest`、允许类型、principalId、tenantId、selectionKey 和参数选择一个当前 Session 中的 Principal。选择器不创建或持久化 Principal。

## 身份与授权注解

### `@AnonymousAccess`

无参数。用于 Controller 类或方法，允许匿名并跳过认证、Principal、Permission 和 Access。不能与受保护访问声明混用。

### `@AuthSubject`

参数注解，支持 `Subject` 和 subjectId `Long`。

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `nullable` | `false` | 未登录时是否注入 null；否则抛认证异常 |

### `@AuthAccount`

参数注解，支持当前 Session 固定的 `Account` 和 accountId `Long`。

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `nullable` | `false` | 未登录时是否注入 null；否则抛认证异常 |

### `@AuthPrincipal`

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `value` | `{}` | 允许的 Principal 类型，`types` 的别名 |
| `types` | `{}` | 允许的 Principal 类型，`value` 的别名 |
| `nullable` | `false` | 仅参数注入生效，缺失时是否允许 null |
| `principalRequired` | `true` | 仅类或方法声明生效，是否必须完成选择 |
| `selection` | `PrincipalSelection.class` | 选择器类型；该默认值表示使用通用 Bean |
| `principalId` | `""` | Principal ID SpEL |
| `tenantId` | `""` | Tenant ID SpEL |
| `selectionKey` | `""` | 业务选择键 SpEL |

参数注入支持 `Principal`、`Long` 和 `PrincipalType`。SpEL 可读取 path、query、header、body、payload 和方法参数。

### `@RequirePermission`

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `value` | `{}` | 功能权限编码，`permissions` 的别名 |
| `permissions` | `{}` | 功能权限编码，`value` 的别名 |
| `roles` | `{}` | 角色编码 |

必须至少声明一个权限或角色，禁止空白和 `*`。全部条件按 AND 执行。

### `@Access`

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `policy` | 无，必填 | `ResourceAccessPolicy<?, ?>` Bean 类型 |
| `action` | 无，必填 | `READ`、`WRITE` 或 `MANAGE` |
| `resourceId` | `""` | 可选单资源 ID SpEL；空表示集合范围 |

该注解可重复。AccessAspect 先读取 Resource 并执行 Cedar，再生成 Criteria。Criteria 必须由 Repository 消费。

## 幂等与并发

### `IdempotentRequest`

写请求实现 `getIdempotencyKey()`。新提交生成新 key，同一提交的网络重试复用原 key。缺少 key 时，启用幂等保护的方法会在业务执行前拒绝请求。

### `@Idempotent`

只能用于方法。

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `operation` | 无，必填 | 跨路由和 Java 重构保持稳定的业务操作编码 |
| `key` | `""` | 显式 key 或 SpEL；为空时从 `IdempotentRequest` 读取 |

```java
@Idempotent(operation = "platform.tenants.create")
```

`operation` 用于隔离不同业务意图，不能直接使用可变的方法名。显式 `key` 优先于 Request。

### `@DistributedLock`

可用于类或方法，方法级优先。

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `key` | 无，必填 | 锁 key 或 SpEL，例如 `"'subject:' + #subjectId"` |
| `waitTimeSeconds` | `-1` | 小于 0 继承全局等待时间 |
| `leaseTimeSeconds` | `-1` | 小于 0 继承全局；`0` 使用 Redisson watchdog；正数为固定租约 |

固定租约必须覆盖业务最长执行时间，否则锁可能在方法结束前释放。执行时间不可预测时使用 watchdog。

### `@RateLimit`

可用于类或方法，方法级优先。同一次调用可以依次消费多个唯一维度。

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `types` | `{IP}` | 至少一个且不得重复的限流维度 |
| `key` | `""` | `CUSTOM` 维度使用的文本或 SpEL |
| `capacity` | `-1` | 正数覆盖桶容量，负数继承全局，0 无效 |
| `refillTokens` | `-1` | 正数覆盖每窗口补充量，负数继承，0 无效 |
| `timeWindowSeconds` | `-1` | 正数覆盖窗口秒数，负数继承，0 无效 |
| `message` | `""` | 限流时可安全展示的消息；空白使用统一消息 |

`RateLimitType`：

| 值 | key 来源 |
| --- | --- |
| `GLOBAL` | 接口全局 |
| `IP` | 客户端 IP |
| `USER` | 优先 Principal，其次 Subject |
| `UA` | User-Agent |
| `CONNECTION` | 当前长连接 |
| `CUSTOM` | `key` 表达式，必须得到有效文本 |

限流用于保护容量，不替代权限、幂等或分布式锁。

## 审计

### `@Audit`

可用于类或方法，方法级优先。

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `module` | 无，必填 | 稳定模块编码，用于筛选和展示 |
| `action` | 无，必填 | 与 HTTP 路由解耦的动作编码 |
| `mode` | `DEFAULT` | 继承全局模式，或覆盖为 `ASYNC`、`SYNC` |
| `payloadType` | `""` | Payload 类型；为空由 Codec 推断 |
| `payload` | `""` | 方法结束后计算的 SpEL；结果必须是 `AuditPayload`、字符串键 Map 或 `null` |
| `summary` | `""` | 方法结束后计算的 SpEL；结果必须是 `MessageReference`、消息键字符串或 `null` |
| `targetType` | `""` | 审计目标类型 |
| `targetId` | `""` | 方法结束后计算的目标 ID SpEL |

```java
@Audit(
        module = PlatformAuditOperations.ACCOUNTS,
        action = PlatformAuditOperations.CHANGE_STATUS,
        targetType = IamResourceTypes.ACCOUNT,
        targetId = "#accountId")
```

表达式可以引用方法参数、`#args`、`#methodName`、`#target`、`#result` 和 `#error`。创建接口可使用 `targetId = "#result.data"` 读取 `ApiResponse` 中的新主键。
module 和 action 在所属领域的 `AuditOperationContributor` 中声明并注册，业务注解不直接填写字符串。启动目录和契约测试会拒绝未注册组合。

### `AuditPayloadCodec<P>`

| 方法 | 说明 |
| --- | --- |
| `payloadType()` | 跨 Java 类重命名保持稳定的类型编码 |
| `payloadVersion()` | JSON 结构版本，默认 1 |
| `payloadClass()` | Codec 唯一拥有的 Payload 类 |
| `encode(ObjectMapper, payload)` | 默认 `valueToTree`，可覆盖 |
| `decode(ObjectMapper, JsonNode)` | 默认按 payloadClass 转换，可覆盖 |

新增版本时要保证历史日志仍可解码。审计 Payload 不应包含密码、token、大段正文或未经脱敏的敏感字段。

## 错误码

### `@CodeRange`

用于实现 `BizErrorCode` 的枚举类型。

| 参数 | 说明 |
| --- | --- |
| `start` | 闭区间起始错误码 |
| `end` | 闭区间结束错误码 |

应用启动时校验枚举内错误码是否落入号段，并检查不同枚举的号段冲突。领域错误使用稳定业务码和安全消息，HTTP 分类由 `ResponseCode` 表达。

每个业务模块拥有自己的错误码枚举、独立号段和 `error.<module>.*` 消息命名空间。错误码按业务概念归属，不按当前数据库表、Repository 或调用入口归类；例如角色和租户错误分别属于 `RoleErrorCode`、`TenantErrorCode`，而 Subject、Account、Credential、Principal 和 DEFAULT Profile 错误属于 `IamErrorCode`，即使底层写入暂时由 IAM Repository 完成也是如此。

`BizException` 从 `BizErrorCode` 读取消息键。业务调用方只能补充消息模板参数或原始 cause，不能覆盖错误码声明的消息键。

```java
throw new BizException(SystemErrorCode.MENU_PARENT_NOT_FOUND);
throw new BizException(IamErrorCode.CREDENTIAL_ACCOUNT_TOO_LONG, 128);
```

## 输入校验

这些约束都只校验格式，不表达必填；必填字段需要组合 `@NotNull`、`@NotBlank` 等 Bean Validation 注解。null 或空白值通常交给必填约束处理。

所有自定义校验都具有 Bean Validation 标准参数：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `message` | 各注解的默认中文消息 | 校验失败消息 |
| `groups` | `{}` | 校验分组 |
| `payload` | `{}` | 校验负载 |

### `@BizId`

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `mode` | `CODE` | `NUM`、`CODE` 或 `CUSTOM` |
| `min` | `1` | 字符串最小长度 |
| `max` | `64` | 字符串最大长度 |
| `regexp` | `""` | `CUSTOM` 使用的正则；CUSTOM 下不能为空 |

`NUM` 接受正整数 Java 类型、`BigInteger` 或仅数字且大于零的字符串；浮点数不接受。`CODE` 要求字母或数字开头，后续允许字母、数字、下划线和中划线。输入不会自动 trim，首尾空白直接失败。

```java
@NotBlank
@BizId(max = 64)
private String tenantCode;

@NotNull
@BizId(mode = BizIdMode.NUM)
private Long tenantId;
```

### `@DisplayName`

除标准参数外提供 `max`，默认 `128`。允许普通空格，禁止控制字符。适用于账号、租户等面向界面展示的短名称。

### `@SafeText`

除标准参数外提供 `max`，默认 `255`。禁止控制字符，不处理敏感词、HTML 清洗或 Markdown 安全。适用于备注、描述等普通文本。

### `@PhoneNumber`

只校验中国大陆手机号基础格式 `1[3-9]` 加九位数字，不验证号码是否真实存在或当前号段运营状态。

### `@MacAddress`

接受大小写十六进制、冒号分隔的六组格式，例如 `00:1A:2B:3C:4D:5E`。不接受连字符分隔或无分隔格式。

## Web 契约

| 类型 | 说明 |
| --- | --- |
| `ApiResponse<T>` | 统一业务码、消息、data、traceId 和时间戳 |
| `PageQuery` / `PageData<T>` | 页码分页与稳定响应结构 |
| `CursorQuery` / `CursorPageData<T>` | 数据持续变化场景的游标分页 |
| `VersionedRequest` | 写请求携带乐观锁 version |

Request 负责传输输入和格式校验，Service 使用显式业务参数，View 负责对外字段。HTTP 状态和统一错误语义见[HTTP 接口](./http)。

## 基础能力接口

| 接口 | 责任 | 默认实现或入口 |
| --- | --- | --- |
| `IdempotencyStore` | 幂等处理权、指纹和成功响应 | `RedisIdempotencyStore` |
| `DistributedLockExecutor` | 集群锁获取与释放 | `RedissonDistributedLockExecutor` |
| `RateLimiter` | 消费限流桶 | `Bucket4jRedisRateLimiter` |
| `IdGenerator` | 生成 Long 与字符串 ID | `ClusterIdGenerator` |
| `MessagePublisher` | DIRECT、AFTER_COMMIT、OUTBOX 发布 | 消息模块 |
| `RealtimePublisher` | 向实时目标发布事件 | 实时通信模块 |
| `FileStorage` | 对象读写与预签名地址 | Local、MinIO |
| `FileSignaturePolicy` | 业务场景上传和读取授权 | 由业务模块实现 |

代码示例见[并发、重复执行与审计](../development/concurrency-and-audit)、[消息发布](../infrastructure/messaging)、[实时通信](../infrastructure/realtime)和[文件服务](../infrastructure/file)。
