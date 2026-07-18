# 缓存

脚手架提供两个 Spring Cache 管理器。业务代码通过 Spring Cache 抽象使用，不直接依赖 `RedisTemplate`。

| Bean 名称 | 实现 | 范围 | 默认用途 |
| --- | --- | --- | --- |
| `cacheManager` / `redisCacheManager` | `RedisCacheManager` | 多实例共享 | 多节点都需要看到的缓存 |
| `localCacheManager` | `CaffeineCacheManager` | 当前 JVM | 允许节点间短暂不一致的热点数据 |

Redis 管理器是 `@Primary`。没有显式指定 `cacheManager` 的 `@Cacheable` 会使用 Redis。

## Redis 缓存

```java
@Cacheable(
        cacheNames = "tenant-summary",
        key = "#tenantId")
public TenantSummary requireSummary(Long tenantId) {
    return repository.requireSummary(tenantId);
}
```

默认 key 前缀为 `sca4dj:`，TTL 为 30 分钟。key 使用字符串序列化，value 使用项目 `ObjectMapper` 的 JSON 序列化，并保留反序列化所需类型信息。null 不进入缓存。

多个实例读取同一个 Redis 缓存，但缓存不会自动理解数据库事务。写操作应在成功提交后清理：

```java
@CacheEvict(
        cacheNames = "tenant-summary",
        key = "#tenantId")
public void evictTenantSummary(Long tenantId) {
}
```

若写方法可能回滚，优先在事务提交后触发失效，避免数据库仍是旧值但缓存已被其他请求重新填充。

## 本地缓存

```java
@Cacheable(
        cacheManager = CacheConfig.LOCAL_CACHE_MANAGER,
        cacheNames = "public-dictionary",
        key = "#type")
public Dictionary loadDictionary(String type) {
    return repository.loadDictionary(type);
}
```

本地缓存默认写入后 5 分钟过期，最多 1000 条。每个实例拥有独立内容，清理 A 节点不会清理 B 节点，因此只适合：

- 配置变化很少且允许短时间旧值。
- 可以通过 TTL 自然恢复。
- 只影响性能，不影响权限、额度或业务正确性。

Subject、Account 和 Principal 状态、SessionPermission、幂等记录、锁和限流记录不能放入本地缓存。这些数据需要在所有应用实例之间共享。

## 复杂缓存

缓存涉及组合 key、批量回填、防击穿或多个模块同时失效时，在所属业务模块的 `cache` 包中提供业务接口，例如 `ProductCatalogCache`，不要让调用方直接执行 Redis 命令。

实现前确认：

1. 缓存数据原本从哪里读取。
2. key 是否包含 tenantId、principalId 等作用域。
3. 哪些写操作负责失效。
4. 事务回滚时如何处理。
5. Redis 不可用时是回源、降级还是失败。
6. value 结构升级后如何兼容或换前缀。

## 配置

| 配置 | 默认值 |
| --- | --- |
| `SCA4DJ_CACHE_KEY_PREFIX` | `sca4dj` |
| `SCA4DJ_CACHE_REDIS_TTL` | `PT30M` |
| `SCA4DJ_CACHE_LOCAL_TTL` | `PT5M` |
| `SCA4DJ_CACHE_LOCAL_MAXIMUM_SIZE` | `1000` |

其他环境变量见[配置项](../reference/configuration#id-与缓存)。
