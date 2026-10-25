# 并发规则

本页规定乐观锁、幂等、分布式锁和限流的选择条件与组合顺序。本页适用于设计写入接口、并发控制和限流声明的开发者。读完后，开发者能判断一个入口应使用哪种机制，以及这些机制各自不能替代什么。

## 乐观锁

`Version` 只表示数据库中真实存在的非负版本。不得使用负数、`disabled` 或其他特殊值绕过检查。

HTTP 写入默认不要求 ETag 和 `If-Match`。大多数业务对象不会频繁被多人同时编辑。字段级 Patch、数据库约束、原子状态条件和服务端读取当前值，已经能处理多数真实竞争。

不得因为表中存在 `version`，或者理论上可能发生并发写入，就给每个接口增加 HTTP 前置条件。

只有同时满足以下两个条件时，才使用 ETag 和 `If-Match`：

1. 调用方先读取稳定资源，并根据这份快照生成后续修改。
2. 旧快照写入会以现实可发生的方式，静默覆盖重要的新内容。

选择时还需判断以下内容：

- 同一对象实际由多少人或自动流程修改。
- 竞争发生的频率。
- 被覆盖内容的业务代价。
- 一个 `Version` 是否覆盖用户看到的完整内容。
- 客户端收到 412 后，能否重新读取并让用户比较或重新编辑。

低频理论风险，且覆盖结果可以接受时，不增加这套交互成本。

以下场景通常不使用 ETag 和 `If-Match`：

- 列表、搜索、分页和统计；
- 创建、追加和事件记录；
- 只写请求中明确出现的字段，且允许后写覆盖前写的 Patch；
- 取消、启停、重试、设为默认等由服务端根据当前状态执行的命令；
- 消息、任务和模块内部调用。

这些入口分别使用字段级写入、唯一约束、状态条件、幂等或普通数据库乐观锁，保证自己的正确性。删除只有在业务要求「删除用户刚确认的那个内容版本」时才使用 `If-Match`。不能仅因删除不可逆，就机械添加。

当前 HTTP 接口约定没有实现 `If-None-Match` 和 304 缓存验证，因此不为了通用缓存单独发布 ETag。读取接口返回的 ETag，应对应一个确实需要 `If-Match` 的后续编辑流程。修改成功后，可以返回新 ETag，让客户端继续编辑同一资源，而不必立即重读。

既有接口不要求脱离相关业务任务，批量删除 `@IfMatch`。修改该功能时，再按上述实际场景复核。

确实需要避免客户端覆盖新内容的 HTTP 方法，在 Controller 声明 `@IfMatch`。

- IfMatchInterceptor 解析单个强 ETag。
- CallLifecycleAdvisor 再把版本条件登记为该方法 CallExecution 的成功要求。Controller 和 Service 不接收版本参数。
- 根资源 Repository 读取当前对象后，调用 `VersionConditions.resolve(currentVersion)`。它把得到的版本和零行策略，用于同一条条件 SQL，并同时标记版本条件已经消费。
- 缺少 `If-Match` 时返回 428。格式错误或版本已经变化时返回 412。
- 业务方法正常返回，但 Repository 没有消费条件时，在事务提交前或方法离开生命周期前抛出失败。

普通 HTTP、消息、任务和模块内部调用没有请求版本条件。`resolve(...)` 使用 Repository 刚读取的当前版本，执行普通乐观锁。冲突表示内部竞争，并返回 409。

Repository 的公开方法不因为底层 SQL 需要版本，就把版本参数逐层传给 Service。

覆盖旧值不会破坏业务不变量时，可以提供名称明确的无条件 Repository 方法，例如 `updateLastSeenAt(...)`。这类方法不能使用特殊 `Version`、默认参数或全局开关隐藏绕过行为。禁止先查版本再无条件写，也禁止给所有表机械增加版本列。

## 幂等

只对允许重放的入口使用 `@Idempotent`。

Redis IdempotencyStore 在调用业务方法前，原子取得处理权。它区分 `ACQUIRED`、`REPLAY`、`IN_PROGRESS` 和 `CONFLICT`。

key 与方法参数请求指纹共同校验。同一 key 对应不同请求时，必须冲突。

成功结果的保留期可以通过 `retentionSeconds` 覆盖 `sca4dj.redis.idempotency.result-retention`。该配置默认为一天。处理中租约仍由 `processing-ttl` 单独控制。

幂等不能替代业务唯一约束。

## 分布式锁

只有并发执行会破坏跨事务状态、重复执行外部副作用，或让多步写入无法恢复时，才使用分布式锁。数据库唯一约束、条件更新或 `version` 已经能把竞争转换成明确冲突时，不再叠加 Redis 锁。

锁 key 必须唯一对应竞争资源。角色、Principal、设备下行、设备计划等独立对象，按自己的稳定标识分别加锁。不使用模块级或应用级全局大锁。

取得锁后再开始数据库事务，并在事务中重新查询当前值。锁内任务必须覆盖事务提交，以及该资源必须串行的外部操作。锁不能在事务提交前提前释放。

Core Spring 的 RedisDistributedLock 使用 Redisson watchdog。任务没有结束时，锁自动续期。它不使用按预估耗时设置的固定租约。

同一线程嵌套调用相同 LockKey 时，直接复用已经持有的锁，不再次访问 Redis。最外层任务结束后，才释放锁。

同步任务结束时无法确认锁所有权，会报告失败，不会返回成功。

返回 CompletionStage 的锁内方法，在入口线程等待拿锁并启动目标。目标完成后，使用逻辑 owner ID 解锁。取消返回值不会提前释放仍在运行的目标。

不同锁键仍然独立竞争。调用方必须保持固定的多锁顺序。

结束时检查只能发现已经失去所有权，不能阻止锁过期后另一实例与原任务同时写入。关键写入仍须使用数据库条件更新、版本或外部系统提供的 fencing token，约束过期持有者。不能仅依赖 Redis 锁，保证失联后的互斥。

已取得业务结果但解锁失败时，声明式锁把故障记录为完成失败，幂等阶段仍保存结果供同键重放。直接使用 DistributedLock 的调用则收到解锁异常，需要业务自身保证重试安全。

参数已经包含 LockKey 时，可以声明 `@Locked(key = "#lockKey", waitMillis = 1000)`。否则，通过 `keyResolver` 构造键。两种方式必须二选一。表达式保持 LockKey 的资源编码和动态标识，不将任意字符串自动转换成锁键。最大等待时间仍由 `waitMillis` 明确声明。

JVM `synchronized`、ReentrantLock 和进程内缓存只能协调当前实例，不能用于保护多实例共享的数据库或外部系统。它们仅适合明确属于单 JVM 的可变状态，并且不能与 Redis 锁表达同一个业务互斥范围。

## 限流

`@RateLimited` 对一个具体方法使用 Redis 共享令牌桶。

- `permits` 是桶容量。
- `windowSeconds` 是从空桶连续补满所需的时间。
- 不声明 `dimensions` 时，默认使用 `GLOBAL`。

Bucket4j 按经过时间连续补充令牌。不存在「窗口结束后一次性清零」的情况。

| `RateLimit.Dim` | 每个额度对应的对象 | 使用条件 |
|---|---|---|
| `GLOBAL` | 具体目标类型和完整方法签名的全部调用 | 所有调用均可使用 |
| `IP` | HTTP 请求的客户端 IP | 调用生命周期中必须存在 HTTP 请求快照 |
| `UA` | HTTP 请求的 User-Agent；缺失 User-Agent 的请求共用一个额度 | 调用生命周期中必须存在 HTTP 请求快照 |
| `SUBJECT` | 当前认证 Subject | 调用生命周期中必须存在 Subject |
| `ACCOUNT` | 当前登录 Account | 调用生命周期中必须存在 Account |

Subject 与 Account 是两个独立维度。限流声明不按 Principal 计算额度。

需要按具体业务对象限流时，可以通过 `key = "#request.phone"` 直接读取非空白业务键。也可以实现 `CallValueResolver<String>`，并通过 `keyResolver` 返回业务键。

`key` 与 `keyResolver` 互斥。自定义键默认作为 `GLOBAL` 之外的额外维度。声明 `dimensions = {}` 后，可以只使用自定义维度。

声明多个内置维度，或同时使用自定义键时，每个维度有独立的 Redis 令牌桶。所有桶使用同一容量和补充时间。

CallLifecycle 会判断全部维度，并采用最长等待时间生成拒绝结果。各桶分别原子消费。前一个维度已经消费的令牌，不会因为后一个维度拒绝而退回。

以下情况会使当前调用失败：重复维度；空维度且没有自定义解析器；缺少维度需要的调用数据。

`@RateLimited` 把具体方法签名、IP、User-Agent、Subject、Account 和自定义值，转换为 SHA-256 摘要。Redis key 不保存这些原始值。

Core Spring 使用 Bucket4j Redis CAS 注册共享 RedisRateLimiter。`sca4dj.redis.rate-limit.keep-after-refill` 默认为一小时，表示额度完全恢复后，继续保留桶状态的时间。

Redis 是 Core Spring 的必需基础设施。RateLimiter、IdempotencyStore 或 DistributedLock 任一 Bean 缺失时，CallLifecycle 无法创建，应用启动失败。不会注册内存实现、空实现，也不会延迟到第一次调用时才失败。

HTTP 调用超过额度时，返回 `429 Too Many Requests`、Core 错误码 `1005` 和正整数秒数的 `Retry-After`。SSE 请求保留 429 与 `Retry-After`，但不返回 JSON 错误体。

## 组合顺序

CallLifecycleConfiguration 固定内置参与者的相对顺序，从外到内依次为：

1. 审计外层；
2. 身份与 Principal 选择；
3. 权限；
4. 限流；
5. 幂等处理权；
6. 分布式锁；
7. 业务方法及其事务；
8. 预提交条件检验；
9. 幂等结果保存；
10. 审计记录。

锁位于业务方法事务之外。权限范围和 `If-Match` 等条件在事务提交前检查，并在无事务方法正常返回后再次检查。

业务失败时，释放仍属于当前 token 的幂等处理权。业务已经成功但清理失败时，保留可重放结果，并报告完成故障。

## 选择原则

数据库约束保证数据有效。乐观锁处理更新冲突。幂等处理重放。分布式锁处理必须串行的副作用。限流处理频率。这些机制之间禁止互相替代。

并发经版本号或租约表达，不依赖进程内状态，与[分布式锁](/capabilities/data-configuration/distributed-lock)和[数据访问与持久化](/capabilities/data-configuration/data-access-and-persistence)的做法一致。

## 示例

以下示例用「文档」说明各机制的位置。`DocumentService`、`DocumentRepository`、`DocumentMapper`、`DocumentRequest` 和 `DocumentView` 由应用定义。

需要避免覆盖他人新内容的编辑接口，在 Controller 声明 `@IfMatch`：

```java
@PutMapping("/documents/{id}")
@IfMatch
public DocumentView replace(@PathVariable long id, @RequestBody DocumentRequest request) {
    return documentService.replace(id, request);
}
```

Repository 读取当前对象后，用 `VersionConditions.resolve` 取得版本条件，并在同一条 SQL 中使用：

```java
public DocumentView replace(long id, String title, String body) {
    Document current = find(id).orElseThrow(() -> new BusinessException(DocumentErrorCode.NOT_FOUND));
    VersionCondition condition = versionConditions.resolve(current.version());
    int rows = mapper.replace(id, title, body, condition.expected());
    AffectedRowCounts.requireSingle(rows, condition.whenZero(), "文档");
    return find(id).orElseThrow();
}
```

`DocumentMapper.replace` 的 SQL 使用 `WHERE id = #{id} AND version = #{expected}`，并在更新时递增 `version`。`DocumentErrorCode` 由应用定义，格式见[错误码与业务异常](/capabilities/foundation/error-model)。

`@IfMatch` 请求缺少 `If-Match` 时返回 428。版本已变化时返回 412。非 HTTP 调用没有请求版本条件，零行表示内部竞争，返回 409。

需要幂等、串行和限流的创建入口，在同一个方法上组合声明。`AttachmentLockKeyResolver` 的写法见[分布式锁](/capabilities/data-configuration/distributed-lock)：

```java
@RateLimited(dimensions = {RateLimit.Dim.SUBJECT}, permits = 10, windowSeconds = 60)
@Idempotent(retentionSeconds = 86_400)
@Locked(keyResolver = AttachmentLockKeyResolver.class, waitMillis = 3000)
@Transactional
public AttachmentView attach(long documentId, AttachRequest request) {
    return attachmentService.attach(documentId, request);
}
```

示例中的 `@Idempotent` 没有声明 `key`，HTTP 请求通过 `Idempotency-Key` 提供幂等键。

调用生命周期按「限流、幂等、锁、事务」的顺序进入，业务方法和事务在锁内执行。

## 扩展方式

应用通过自定义 `CallValueResolver` 提供锁键或限流键，并在注解的 `keyResolver` 属性中引用该类型。内置拦截器读取注解后调用解析器。

应用声明自己的 `DistributedLock` Bean 时，Core Spring 不再创建默认实现。
