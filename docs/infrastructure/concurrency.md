# 并发控制

## VersionedRequest

`VersionedRequest` 统一表示客户端基于哪个版本提交。Repository 使用 ID、访问范围、状态和版本进行原子更新，冲突抛 `ConcurrencyConflictException` 并映射 409。

## IdempotentRequest

`IdempotentRequest` 与 `@Idempotent` 为可重放入口建立 Redis 记录。`IdempotencyStore` 保存请求指纹、`PROCESSING` 或 `SUCCEEDED` 结果；同 key 不同请求冲突，处理中返回 retry-after。

## 分布式锁

`@DistributedLock` 通过 SpEL 生成业务 key，`DistributedLockExecutor` 的 Redisson 实现负责等待、租期和释放。需要动态流程时使用 `DistributedLockTemplate` 与 `LockedOperation`。

## Redis 限流

`@RateLimit`、`RateLimitRule` 和 `RateLimitKeyGenerator` 建立规则，`Bucket4jRedisRateLimiter` 让多个实例共享令牌桶。HTTP 和 STOMP 可使用不同容量与 key 维度。

## 选择并发方式

| 方式 | 解决问题 | 不解决 |
| --- | --- | --- |
| 乐观锁 | 同一记录被并发更新 | 请求重放 |
| 幂等 | 相同请求重复执行 | 不同请求争用资源 |
| 分布式锁 | 必须串行的跨实例副作用 | 数据库完整性 |
| 限流 | 频率和容量保护 | 业务授权 |

## 失败结果

| 情况 | HTTP 结果 |
| --- | --- |
| version 已变化 | 409，并可返回 `X-Client-State-Refresh` |
| 缺少幂等 key | 400 |
| 同 key 的首次请求仍在执行 | 409 和 `Retry-After` |
| 同 key 对应不同请求内容 | 409 |
| 等待分布式锁超时 | 409 |
| 超过限流容量 | 429 和 `Retry-After` |

Redis 不可用时，幂等、分布式锁和共享限流不能退化为单实例内存实现。数据库唯一索引、version 条件和事务仍需保留。

代码示例见[并发与审计开发](/development/concurrency-and-audit.md)，使用限制见[并发规范](/standards/facilities/concurrency.md)。
