# 应用分层与调用链

## 完整请求链路

```text
HTTP request
  → CurrentRequestFilter
  → AuthInterceptor
  → PrincipalInterceptor
  → permission / access aspects
  → Controller
  → Service
  → Repository
  → Mapper
  → PostgreSQL
  → Service return value
  → View.from(domain value)
  → ApiResponse<T>
  → ClientState response advice
```

Filter 和拦截器先读取 traceId、Session、Subject、Account 和 Principal。Controller 绑定请求参数并调用 Service。Service 执行校验和数据修改，Repository 组织查询条件，Mapper 执行 SQL。Controller 最后把 Service 的返回对象转换为 View。

## Controller

Controller 负责路由、Request 绑定、Bean Validation、认证/权限/访问声明、HTTP 状态与 View 转换。它不承载事务性业务流程，不直接组合多次 Mapper 调用。

公共、产品端和管理端 Controller 按流程组织。相同资源若权限、状态机、副作用或审计不同，应保留两个清晰流程。

## Service

Service API 使用业务语言和显式参数，可以直接复用 Entity，也可以返回确有独立语义的业务类型。实现完整展示校验、构建、持久化和副作用；事务通常由 Service 建立。Service 不返回 HTTP View，也不感知前端组件。Controller 必须把返回值转换为 View 或其他明确的 API 输出，不能直接把 Entity 放进响应。

## Repository

Repository 为领域提供持久化语义，如 `findAccessibleMember`、`updateStatus`。它封装 Mapper、结果转换和访问条件，避免业务层理解 SQL 形状。Repository 不是通用 CRUD Manager。

## Mapper

Mapper 负责 SQL 和数据库字段映射。简单单表操作使用 MyBatis-Flex BaseMapper；动态条件、联表、聚合、批量查询使用自定义 Mapper 和 XML。列表查询需要检查实际 SQL 次数，避免为每一行再次查询关联数据。

## Entity 与业务模型

Entity 映射表结构，可以由 Service 和持久化流程直接复用。独立业务类型只在组合多张表、裁剪字段或形成独立业务概念时建立；不得为了不返回 Entity 而复制一层字段完全相同的类型，也不得把 Entity 直接作为 HTTP 输出。

## Request 与 View

Request 对应 HTTP 请求体或查询参数，并声明长度、格式等基础校验。View 对应响应中的 `data`，通过 `from(...)` 从 Service 返回对象构造。`BaseView` 和 Jackson 序列化器只处理输出字段，不查询数据库。

## 依赖方向

Controller 依赖领域 Service 和公共接口；Service 依赖 Repository 以及消息、文件等接口；Repository 依赖 Mapper。底层不得反向依赖具体页面或 Controller。
