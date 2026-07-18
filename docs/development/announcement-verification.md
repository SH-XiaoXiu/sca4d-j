# 测试与验收

公告模块需要验证归属隔离、状态机、权限、并发、幂等、审计和事件发布。

## 测试数据

准备四个会话快照：

| 会话 | Principal | 权限 |
| --- | --- | --- |
| 普通用户 | `DEFAULT` | 无公告管理权限 |
| 平台运营 | `PLATFORM` | `platform:announcements:read`、`create`、`update`、`delete` |
| 租户 A 运营 | `TENANT(1001)` | `tenant:announcements:read`、`create`、`update`、`delete` |
| 租户 B 只读成员 | `TENANT(1002)` | `tenant:announcements:read` |

数据库准备平台公告、租户 A 公告和租户 B 公告，各自包含 DRAFT、PUBLISHED、CLOSED 和已删除记录。

## Repository 测试

使用真实 PostgreSQL 或 Testcontainers 验证：

- `pageGlobalPublished` 只返回 `tenant_id IS NULL` 的 PUBLISHED 记录。
- `pageTenantPublished` 在 tenantId 1001 的 Criteria 下只返回租户 A 公告。
- 平台管理 Criteria 只查询 `tenant_id IS NULL`。
- 租户管理 Criteria 只查询当前 tenantId。
- 租户 A 的 Criteria 查询租户 B announcementId 时返回空。
- 创建平台公告写入 null tenantId，创建租户公告写入当前 tenantId。
- `created_by_principal_id` 来自当前 actor。
- 发布同时写入状态、`published_at` 和 `published_by_principal_id`。
- 更新、状态变化和删除同时匹配 announcementId、tenantId、version 和 deleted。
- 租户 A 使用租户 B announcementId 执行写入时影响零行。
- 逻辑删除后所有公开与管理查询都不可见。

SQL 语义需要直接断言数据库结果，不使用 Mock Mapper 代替。

## Service 测试

| 场景 | 预期结果 |
| --- | --- |
| 平台创建 | DRAFT、tenantId 为 null、创建 Principal 正确 |
| 租户创建 | DRAFT、tenantId 为当前租户、创建 Principal 正确 |
| 草稿发布 | PUBLISHED，写入发布时间和发布 Principal |
| 已发布关闭 | CLOSED，保留最近发布信息 |
| 已关闭重新发布 | PUBLISHED，刷新发布时间和发布 Principal |
| 草稿直接关闭 | 返回状态冲突，不执行写入和事件发布 |
| 旧版本更新 | 返回 409，不覆盖新内容 |
| 删除 | 当前范围内逻辑删除 |

发布平台公告后，Realtime 目标是 `announcements.global`；发布租户公告后，目标是对应 tenantId。两种发布都写入 `announcement.published` Outbox，Payload 的 tenantId 与公告归属一致。

发布事务回滚时，状态和 Outbox 一起回滚，Realtime 的 afterCommit 回调不发送。

## Principal 与权限测试

- 未登录访问四组入口均返回 401。
- `DEFAULT Principal` 可以读取平台已发布公告。
- 没有租户成员 Principal 时，不能访问该租户公告。
- `TENANT(1001)` 访问 `/tenants/1002/announcements` 返回 403。
- `PLATFORM Principal` 不能进入租户管理 Controller。
- `TENANT Principal` 不能进入平台管理 Controller。
- 只有 READ 权限时可以查询管理列表和详情，创建、更新、删除返回 403。
- DELETE 权限不能替代 READ、CREATE 或 UPDATE；同一权限声明没有隐含包含关系。

权限测试使用真实 `SessionPermission` 快照，不跳过 AccessAspect。

## 跨租户隔离

以下请求都要单独测试：

```text
租户 A 读取租户 B 公告列表
租户 A 读取租户 B 公告详情
租户 A 更新租户 B 公告
租户 A 发布或关闭租户 B 公告
租户 A 删除租户 B 公告
```

列表不能出现租户 B 数据；详情返回不存在；原子更新和删除不得影响任何行。数据库断言租户 B 的内容、状态、版本和删除标记保持不变。

还要验证创建 Request 即使额外提交 tenantId 或 principalId，也不能改变服务端根据当前 Principal 确定的归属。严格 JSON 配置拒绝未知字段时返回 400；允许未知字段时也必须忽略其业务含义。

## 幂等与并发

平台创建与租户创建使用不同 `operation`：

```text
platform.announcements.create
tenant.announcements.create
```

同一 operation、同一 `idempotencyKey` 和相同请求只创建一次。相同 key 携带不同正文返回冲突。第一次请求仍在处理中时，重复请求返回处理中冲突和 `Retry-After`。

两个请求使用同一 version 更新同一公告，只允许一个成功。跨租户写入与旧版本写入都不能影响目标记录。

## 审计验证

按照当前 `@Audit` 实现检查：

- 平台操作使用 `platform.announcements` 模块。
- 租户操作使用 `tenant.announcements` 模块。
- Subject、Account 和 Principal 来自当前请求。
- 更新、状态变化和删除能从路径记录 announcementId。
- 创建记录包含 `target_type=announcement` 和新生成的 announcementId。
- 更新、状态变化和删除记录使用路径中的 announcementId。
- 成功、失败和 traceId 能正确记录。

创建接口返回后，测试查询 `sys_log`，确认 targetId 等于响应中的 announcementId。状态修改测试确认操作编码、目标 ID、执行结果和 traceId 正确。

## OpenAPI

运行中的 `/v3/api-docs` 应包含 16 个公告接口：

- 2 个平台公告阅读接口。
- 2 个租户公告阅读接口。
- 6 个平台管理接口。
- 6 个租户管理接口。

同时确认：

- tenantId 只出现在租户路径，不出现在创建 Request。
- Request 的必填、长度、枚举和 version 约束正确。
- `@AuthSubject`、`@AuthAccount`、`@AuthPrincipal` 注入参数不进入 OpenAPI。
- 产品 View 不包含状态、version 和 deleted。
- 管理 View 包含 tenantId、操作者和 version。
- 400、401、403、404、409 与统一响应结构一致。

## 管理端联调

- 平台工作区调用 `/mg/platform/announcements`。
- 租户工作区根据当前 tenantId 调用 `/mg/tenant/tenants/{tenantId}/announcements`。
- 切换租户后重新创建 API 或更新 root，不能沿用上一个 tenantId。
- 创建失败重试复用同一个 `idempotencyKey`。
- 写入使用列表或详情中的最新 version。
- 403 不显示为数据不存在；409 后重新加载记录。
- 平台 Topic 或租户定向事件到达后刷新对应列表。

## 完成标准

- Flyway 迁移可在空库和已有库执行。
- 平台与租户 Repository 范围测试通过。
- 状态机、事务、Realtime 和 Outbox 测试通过。
- Principal、Permission 和 Access 测试通过。
- 跨租户读写隔离测试通过。
- OpenAPI 与管理端公共类型一致。
- 平台和租户权限都能通过角色分配。
- 创建、更新、状态变化和删除的操作日志包含正确的目标 ID、执行结果和 traceId。
