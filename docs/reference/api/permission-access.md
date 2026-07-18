# 权限与资源访问

## 权限目录

包：`com.sca4d.common.permission`

| 类型 | 主要成员 | 说明 |
| --- | --- | --- |
| `PermissionModule` | `permissionTree()` | 领域权限目录入口 |
| `PermissionNode` | `module(...)`、`resource(...)`、`action(...)` | 构建模块、资源和动作树 |
| `PermissionItem` | code、name、type、sortOrder | 展平后的权限项 |
| `PermissionType` | `MODULE`、`RESOURCE`、`ACTION` | 权限节点类型 |
| `PermissionCatalog` | 查询树、编码和可分配项 | 聚合所有 PermissionModule |
| `SessionPermission` | principalId、roleCodes、permissionCodes | 当前 Principal 的 RBAC 快照 |
| `PermissionChecker` | 检查角色和权限 | 默认实现 `SaTokenPermissionChecker` |
| `@RequirePermission` | value | Controller 或 STOMP 方法权限声明 |

当前目录贡献者：`PlatformPermissions`、`TenantPermissions`、`SystemPermissions`、`RealtimePermissions`、`NotificationPermissions`、`ImPermissions`。`iam_permission.granted_by_default` 标记不经过角色直接进入所有 Principal Session 的权限；这些节点不进入角色可分配目录。

## Access

包：`com.sca4d.common.access` 和 `com.sca4d.common.permission`

| 类型 | 主要成员 | 说明 |
| --- | --- | --- |
| `AccessAction` | `READ`、`WRITE`、`MANAGE`、`permits(...)` | 领域访问等级 |
| `AccessCriteria` | 标记接口 | 每个领域定义具体查询条件类型 |
| `Policy<R>` | `resourceType()`、`allows(...)` | 已解析、校验并缓存的 Cedar Policy |
| `Rule<R>` | 由 `Rules` 创建 | 类型安全 Cedar 条件 |
| `Rules` | `policy(...)`、`all(...)`、`any(...)`、`not(...)`、`tenant(...)`、`owner(...)`、`member(...)`、`grant(...)`、`attribute(...)`、`time(...)` | 普通业务策略 DSL |
| `CedarPolicy<R>` | `schemaResource()`、`policyResource()`、`request(...)` | 引用独立 Cedar 文件的高级接口 |
| `CedarRequest` | Principal、Resource、Context、Entities | 高级策略传给 Cedar 的请求数据 |
| `ResourceAccessPolicy<R, C>` | `policy()`、`resource(...)`、`criteria(...)`、`criteriaType()` | 读取业务资源，执行 Cedar 后生成 Criteria |
| `ResourceAccess` | `require(criteriaType)` | Service 或 Repository 读取当前 Criteria |
| `@Access` | policy、action、resourceId | 声明入口需要生成的 Criteria |
| `AccessAspect` | AOP 入口 | 解析注解并写入 CurrentRequest |
| `AccessPolicy` | Policy 注册和查找 | 拒绝未登记或返回类型错误的 Policy |
| `ResourceAccessDeniedException` | ForbiddenException | 资源访问拒绝 |

`AccessAspect` 先完成 Principal 选择和 RBAC，再解析 `resourceId` SpEL、读取 Resource、执行 Cedar，最后把 Criteria 写入 `CurrentRequest`。`ResourceAccessPolicy` 不返回 MyBatis Wrapper。Repository 必须把 Criteria 字段写入查询、更新和删除条件。

Sa-Token 继续从当前 Principal 的 `SessionPermission` 判断角色和功能权限。Cedar 只判断具体资源关系。列表查询、批量查询和写入仍由 Repository 使用 Criteria 拼接 SQL。

## 租户 ACL

| 包 | 类型 | 主要成员或语义 |
| --- | --- | --- |
| `com.sca4d.tenant` | `TenantAccessService` | 查询和原子替换目标 PLATFORM Principal 的租户 ACL |
| `com.sca4d.tenant.policy` | `TenantAccessScope` | 默认动作、租户覆盖规则和排除集合 |
| `com.sca4d.tenant.policy` | `TenantAccessRule` | 单个租户及其最高 `AccessAction` |
| `com.sca4d.tenant.policy` | `TenantAccessPolicy` | 将当前 Principal ACL 解析为请求级 `TenantAccessCriteria` |
| `com.sca4d.tenant.repository` | `TenantAccessRepository` | ACL 持久化、批量替换和读取 |

PLATFORM Principal 的租户授权使用 `tenant_access_grant`，不写入 Session。RBAC 判断功能是否可用，租户 ACL 决定每个租户允许的最高动作；缺少规则时拒绝访问。

## 当前领域 Policy

| Policy | Criteria | 使用位置 |
| --- | --- | --- |
| `TenantAccessPolicy` | `TenantAccessCriteria` | 租户及其成员、角色、字典等租户归属资源 |
| `ImFileSignaturePolicy` | 文件签名 Criteria | IM 图片上传和读取 |

## 资源引用

资源引用类型见 [Web 与通用类型](/reference/api/web-core.md#id-与资源引用)。当前 Contributor 包括 IAM、系统菜单及系统资源实现；解析时必须应用当前 Principal 的功能权限和领域访问范围。
