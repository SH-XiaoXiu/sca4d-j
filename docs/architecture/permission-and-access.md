# 权限与资源访问

授权分为两层：功能权限回答“能否执行这类动作”，资源访问回答“能对哪些数据执行”。两层都成立，操作才成立。

## 功能权限

权限编码使用 `<domain>:<resources>:<action>`，如 `system:operation-logs:read`。领域通过 `PermissionModule`、`PermissionNode` 和 `PermissionItem` 贡献目录；`PermissionCatalog` 聚合，`@RequirePermission` 声明入口需要的明确权限。

## RBAC 判断

角色绑定权限，Principal 关联角色。`PermissionChecker` 的 Sa-Token 实现读取当前 `SessionPermission` 执行判断。角色码和权限码都不得使用 `*` 作为业务通配；目录中不存在的权限不能靠字符串碰巧通过。

`iam_permission.granted_by_default=true` 表示权限直接进入每个 Principal 的 Session，不需要角色关系。当前实时订阅读取和 SSE 连接权限使用默认授予，保证已认证 Principal 可以接收状态变化。默认授予权限不出现在角色可分配目录中。

## 资源访问

功能权限不会自动限定租户或资源归属。`AccessAction` 表达 `READ`、`WRITE`、`MANAGE` 三个资源访问等级。Controller 使用 `@Access` 指定 `ResourceAccessPolicy<R, C>`，`AccessAspect` 按以下顺序执行：

```text
PrincipalSelection
  -> @RequirePermission / Sa-Token RBAC
  -> resourceId SpEL
  -> ResourceAccessPolicy.resource(...)
  -> Cedar Policy
  -> ResourceAccessPolicy.criteria(...)
  -> CurrentRequest
  -> Repository SQL
```

只有 RBAC 与 Cedar 都允许时才进入 Controller 方法。Cedar 不创建 Session、不读取角色、不生成 SQL，也不替代 Repository 的查询条件。

## Policy 和 Rule

普通业务使用 `Policy<R>`、`Rule<R>` 和 `Rules`。`Rules` 提供 `all`、`any`、`not`、`principal`、`tenant`、`owner`、`member`、`grant`、`attribute` 和 `time`。这些方法生成 Cedar 条件，业务代码不拼 Cedar 字符串。

Entity、Action 和 Context 的授权模型由 main classpath 中独立的 `schema.cedar` 定义。DSL 只生成 permit/forbid Policy，不生成 Schema。

```java
private static final Policy<TenantResource> POLICY = Rules.policy(
        "tenant-access",
        TenantResource.class,
        "/cedar/tenant-access/schema.cedar",
        List.of(
                Rules.<TenantResource>principal(PrincipalType.SYSTEM),
                Rules.tenant(TenantResource::getTenantId, TenantResource::isCollection),
                Rules.all(
                        Rules.<TenantResource>principal(PrincipalType.PLATFORM),
                        Rules.grant(TenantResource::isGranted))),
        List.of());
```

`Rules.policy(...)` 在 `static final Policy` 初始化时读取一次 Schema 资源，为每条 permit/forbid Rule 分配稳定 ID，并调用 Cedar 验证。解析后的 `Schema` 和 `PolicySet` 缓存在 Policy 实例中；请求期间不再读取资源，只构造 Principal、Resource 和 Context Entity。

复杂策略实现 `CedarPolicy<R>`，通过 `schemaResource()` 和 `policyResource()` 同时引用独立 Schema 与 Policy 文件。每条静态策略必须声明唯一 `@id("...")`。`Policy.cedar(...)` 在启动期解析、验证和缓存文件。普通管理页面不提供 Cedar 在线编辑器。

## ResourceAccessPolicy

`ResourceAccessPolicy<R, C>` 是业务资源与 Cedar、Repository 之间的连接点：

- `policy()` 返回已缓存的 `Policy<R>`；
- `resource(...)` 使用固定次数查询读取 Cedar 判断需要的资源和关系；
- `criteria(...)` 复用已读取的资源生成类型安全 `AccessCriteria`；
- `criteriaType()` 声明 Repository 读取的 Criteria 类型。

Policy 明确处理 PLATFORM、TENANT 和 SYSTEM Principal。单资源判断返回 Deny 时不生成 Criteria；集合请求可以先允许进入 Repository，再由 Criteria 返回空集、包含集或排除集。

## AccessCriteria

Criteria 保存 Repository 查询所需的范围，例如允许的 `tenantIds`、`ownerPrincipalId` 或可管理成员 ID。它使用具体 Java 类型，不使用任意 Map。空集合表示不能访问任何记录；访问全部记录使用单独的 `ALL` 模式。

## 持久化资源范围

持久化关系由具体业务模块拥有。当前租户模块使用 PLATFORM Principal 专用 ACL：默认规则与单租户覆盖规则都保存最高 `READ/WRITE/MANAGE` 动作。`TenantAccessPolicy.resource(...)` 一次读取 `tenant_access_grant`，将结果同时用于 Cedar 的单资源判断和 `TenantAccessCriteria`。ACL 不写入 Session。新增关系表前，按 [ACL 表选型参考](/handbook/evaluation-criteria.md#acl-表选型参考)判断是否需要持久化。

## Repository 消费范围

读取、更新和删除都必须把 Criteria 放入 SQL 条件。先按 ID 查出数据再做内存判断可能泄露存在性并制造竞态；无权访问与资源不存在通常使用一致的安全失败语义。

## 权限变更刷新

角色、权限、Principal 状态或领域访问范围变化后，服务端先在事务提交后替换受影响 Account 的共享 Session，再向 Subject 发送 `authentication.session.changed`。在线管理端收到事件后重新读取当前 Principal 的 `/me`；该 Principal 已不可用时改用 DEFAULT，并清理无权访问的工作区标签。

当前请求仍通过 `X-Client-State-*` Header 获得即时反馈。`refresh(target)` 只登记精确目标，`stale()` 单独表示无法限定目标的状态过期。前端不根据响应正文猜测是否需要刷新。
