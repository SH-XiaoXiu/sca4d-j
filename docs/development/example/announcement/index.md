# 实现公告模块

完成后，平台人员可以发布全平台公告，租户人员可以发布本租户公告，登录用户只能读取当前 Principal 可见的内容。

## 前置条件

- 已完成[第一次运行](/handbook/getting-started.md)，后端和 PostgreSQL 可以启动。
- 已了解 [IAM 身份与访问模型](/architecture/iam.md)和[Session、Principal 选择与请求身份](/architecture/identity-and-session.md)。
- 已了解[权限与资源访问](/architecture/permission-and-access.md)。

## 业务要求

- 公告包含标题、正文、状态、所属租户、创建 Principal、发布 Principal、发布时间和版本。
- `tenantId` 为空时是平台公告，非空时只能由对应租户管理和读取。
- 状态按 `DRAFT → PUBLISHED → CLOSED` 变化；已关闭公告可以重新发布。
- 创建请求支持幂等，更新和状态变化使用乐观锁。
- 发布成功后写入 Outbox，并通知在线客户端刷新列表。
- 创建、更新、发布、撤回和删除写入操作日志。

## 实现步骤

1. [创建表、枚举、Entity 和错误码](/development/announcement-model.md)。
2. [实现 Mapper 与 Repository](/development/announcement-persistence.md)。
3. [实现 Service 的查询和状态变化](/development/announcement-service.md)。
4. [添加 Request、View 与 Controller](/development/announcement-web.md)。
5. [添加权限、数据访问检查和管理端页面](/development/announcement-integration.md)。
6. [运行测试并检查 HTTP、数据库、消息和操作日志](/development/announcement-verification.md)。

## 完成结果

- 平台公告与租户公告保存在同一张表中。
- 跨租户读取和修改被拒绝。
- 同一个幂等 key 不会创建两条公告，旧 version 不能覆盖新数据。
- 数据库事务提交后发送实时刷新；RabbitMQ 暂时不可用时 Outbox 记录保留。
- 管理端可以处理加载、空列表、请求失败、无权限和 409 冲突。
