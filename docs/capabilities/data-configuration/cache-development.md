# 缓存规则

本页规定 Spring Cache、Redis 缓存和 Caffeine 本地缓存的使用要求。本页适用于设计、实现和评审缓存代码的开发者。读完后，开发者能判断一项数据是否可以缓存，并确定缓存 Key、失效时机和测试范围。

## 适用范围

本规则适用于 Spring Cache、Redis 缓存和 Caffeine 本地缓存。

## 数据来源

缓存必须能够从数据库或外部服务重新生成。权限、会话、幂等记录、锁、限流记录和业务写入结果，不得只保存在本地缓存。

## 缓存 Key

Key 必须包含读取结果所依赖的 tenantId、principalId、locale 或其他查询条件。禁止让不同租户或不同权限范围共用同一个业务 Key。

## 失效时机

写操作必须在数据库事务提交后失效缓存。事务可能回滚时，禁止在提交前清理缓存后，立即允许其他请求回填旧值。

失效动作经 `TransactionCallbacks` 绑定事务结果，业务代码不自行判断提交状态。其他模块的事务后动作使用同一入口，见[事务提交后回调](/capabilities/foundation/transaction-callbacks)。

## 本地缓存

本地缓存只用于允许节点间短时间不一致的数据。清理一个实例不会清理其他实例。涉及权限、额度、状态机或唯一性判断时，禁止使用本地缓存。

## Value 变更

缓存 Value 的字段或类型发生不兼容变化时，必须更换缓存名称或 Key 前缀，或者提供可以读取旧值的转换。不能假设 Redis 中没有旧数据。

## 验证

测试至少覆盖以下场景：首次回源、命中、事务提交后失效、Redis 不可用、本地缓存过期和不同租户 Key 隔离。

## 示例

以下示例缓存「工作区」摘要。`WorkspaceCache`、`WorkspaceSummary` 和 `WorkspaceRepository` 由应用定义，缓存可以是 Redis 或 Caffeine。

```java
public WorkspaceSummary summary(long tenantId, long workspaceId, Locale locale) {
    String key = "workspace-summary:" + tenantId + ":" + workspaceId + ":" + locale.toLanguageTag();
    WorkspaceSummary cached = workspaceCache.get(key);
    if (cached != null) {
        return cached;
    }
    WorkspaceSummary loaded = workspaceRepository.loadSummary(tenantId, workspaceId, locale);
    workspaceCache.put(key, loaded);
    return loaded;
}

@Transactional
public void rename(long tenantId, long workspaceId, String name) {
    workspaceRepository.rename(tenantId, workspaceId, name);
    TransactionCallbacks.afterCommit(() -> workspaceCache.evictWorkspace(tenantId, workspaceId));
}
```

读取路径的 Key 包含 tenantId、workspaceId 和 locale，不同租户和语言不会共用同一项。

写入路径通过 `TransactionCallbacks.afterCommit` 登记失效动作。事务提交后才清理缓存，事务回滚时动作被丢弃。

`afterCommit` 要求当前线程存在已启用同步的真实事务。调用方可能在事务外时，使用 `afterCommitOrNow`。动作失败的结果见[事务提交后回调](/capabilities/foundation/transaction-callbacks)。
