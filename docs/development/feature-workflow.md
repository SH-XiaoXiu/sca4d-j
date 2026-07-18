# 创建一个业务功能

以下步骤适用于需要数据库表、HTTP 接口和管理页面的业务功能。

## 前置条件

- JDK 21、Docker、Node.js 24 和 pnpm 已安装。
- `docker compose up -d` 已启动 PostgreSQL、Redis、RabbitMQ 和 MinIO。
- `./gradlew :sca4d-server:bootRun` 可以正常启动。
- 已确定用户操作、请求字段、返回字段和错误情况。

## 1. 列出需要修改的文件

以 `order` 模块为例，通常需要以下文件：

```text
sca4d-server/src/main/
├── java/com/sca4d/order/
│   ├── OrderService.java
│   ├── OrderErrorCode.java
│   ├── Order.java
│   ├── repository/OrderRepository.java
│   ├── request/OrderCreateRequest.java
│   └── view/OrderView.java
├── java/com/sca4d/controllers/mg/order/OrderController.java
├── java/com/sca4d/entity/OrderEntity.java
├── java/com/sca4d/mapper/OrderMapper.java
├── resources/db/migration/
└── resources/mapper/OrderMapper.xml
```

如果功能需要管理页面，还要修改 `sca4d-admin/common` 中的 API 和 `sca4d-admin/app` 中的页面、路由与测试。

## 2. 添加数据库迁移

在 `sca4d-server/src/main/resources/db/migration` 新增版本文件。不要修改已经执行过的迁移。

```sql
CREATE TABLE ord_order (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_ord_order_no ON ord_order (tenant_id, order_no);
```

启动应用后检查 Flyway 日志，并在 PostgreSQL 中确认表、索引和字段类型与迁移一致。

## 3. 添加 Entity 和 Mapper

Entity 放在 `com.sca4d.entity`，Mapper 放在 `com.sca4d.mapper`。字段名称和类型必须与数据库一致。

简单的主键查询和单表写入可以使用 `BaseMapper<OrderEntity>`。联表、聚合、动态筛选和批量查询使用具名 Mapper 方法与 XML。

## 4. 编写 Repository

Repository 放在业务包中。它接收业务查询条件，调用 Mapper，并将 Entity 转换为业务对象。

更新语句需要同时检查资源 ID、当前用户可访问的范围和 `version`：

```sql
UPDATE ord_order
SET status = #{status},
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE id = #{orderId}
  AND tenant_id = #{tenantId}
  AND version = #{version}
```

影响行数为 `0` 时，Repository 抛出 `ConcurrencyConflictException` 或业务定义的不存在错误。

## 5. 编写 Service

Service 依次执行：

1. 检查输入和当前状态；
2. 创建或修改业务对象；
3. 调用 Repository；
4. 在事务提交后发布消息或实时更新。

Service 方法返回业务对象或 Result，不返回 HTTP View。`@Transactional` 放在 Spring 可以代理的公共方法上。

业务流程同时按[应用日志规范](/standards/facilities/logging)记录关键决策、状态变化、外部副作用和最终结果。统一访问日志不能替代这些业务阶段日志。

## 6. 添加 Request、View 和 Controller

Request 声明 HTTP 输入和 Bean Validation。View 声明返回字段并提供 `from(...)` 方法。Controller 负责路由、权限注解、Request 绑定和 View 转换。

```java
@PostMapping
@RequirePermission(OrderPermissions.CREATE)
public ApiResponse<OrderView> create(
        @Valid @RequestBody OrderCreateRequest request) {
    Order order = orderService.createOrder(
            request.getOrderNo(), request.getItems());
    return ApiResponse.ok(OrderView.from(order));
}
```

匿名接口必须标注 `@AnonymousAccess`。需要按租户或资源限制数据时，同时添加 `@Access` 和对应 Policy。

## 7. 添加权限和数据访问检查

1. 在领域权限类中定义权限编码。
2. 将权限加入 `PermissionCatalog`。
3. 在 Controller 方法上添加 `@RequirePermission`。
4. 定义领域 `AccessAction` 和 `AccessCriteria`。
5. 使用 `Rules` 声明 `Policy<R>`，并实现 `ResourceAccessPolicy<R, C>`。
6. 在 Repository 的查询和更新 SQL 中使用 Criteria。

使用有功能权限、无功能权限、ACL 动作不足、其他租户和 SYSTEM 任务分别测试。

## 8. 选择并发和审计功能

| 情况 | 使用的功能 |
| --- | --- |
| 两个用户同时修改同一记录 | `VersionedRequest` 和版本更新 |
| 客户端重复提交相同创建请求 | `@Idempotent` |
| 多个实例不能同时执行某个操作 | `@DistributedLock` |
| 登录或发送消息频率过高 | `@RateLimit` |
| 操作需要在管理端查询 | `@Audit` 和 Audit Payload |

数据库唯一约束仍然需要保留。分布式锁和幂等记录不能替代唯一索引。

## 9. 更新管理端

1. 在 `sca4d-admin/common` 添加 API 方法和 TypeScript 类型。
2. 运行 `pnpm api:generate` 和 `pnpm api:check`。
3. 添加页面、路由、菜单和权限判断。
4. 处理加载中、空列表、请求失败、无权限、提交中和 409 冲突。
5. 添加 store、API 和页面测试。

Java `Long` ID 在 TypeScript 中使用 `string`，不要转换为 `number`。

## 10. 运行检查

```bash
./gradlew :sca4d-server:test
./gradlew spotlessCheck spotlessMiscCheck
./gradlew checkEnvExample

cd sca4d-admin
pnpm lint
pnpm typecheck
pnpm test
pnpm build

cd ../docs
pnpm docs:build
```

## 运行结果

功能完成后应能确认：

- 新数据库可以执行全部 Flyway 迁移；
- HTTP 成功、校验失败、未登录、无权限、不存在和 409 响应符合预期；
- 其他租户不能读取或修改当前数据；
- 重复请求和并发更新不会产生重复记录或覆盖新数据；
- 管理页面能显示成功和失败结果；
- OpenAPI、TypeScript 类型和文档使用相同字段与路由。
