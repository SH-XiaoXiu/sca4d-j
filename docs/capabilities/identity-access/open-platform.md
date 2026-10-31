# 平台开放能力

平台开放能力登记外部程序、认证账号、业务主体和可调用的 `Operation`。`OpenClient` 是外部程序注册对象，绑定 `APPLICATION` 类型的 `Subject` 和 `Account`；OpenAPI 与 GraphQL 通过各自的协议适配层发布同一组 `Operation`。

本页说明 Client、Account、Principal、Operation 和协议适配层的关系。需要声明 HTTP 路由时阅读 [OpenAPI 外部调用](/capabilities/identity-access/openapi-client)，需要声明 GraphQL Schema 时阅读 [GraphQL 外部调用](/capabilities/identity-access/open-graphql)。

## 核心关系

```text
OpenClient
  └── accountId → Account（open 认证域）
                    └── subjectId → Subject（APPLICATION）
                                      └── Principal
                                            └── Operation 权限
```

`OpenClient` 不替代 `Subject`、`Account` 或 `Principal`。请求恢复 Token 后，`Call` 保存 Subject 和 Account；具体应用通过 `@CurrentPrincipal` 选择业务 Principal，`@RequirePermission` 再检查业务权限。

平台开放模块保存 `open_client` 和 `open_client_operation`。IAM 继续拥有 Subject、Account 和 Principal 关系，认证模块继续拥有 Shared Secret、HMAC 和 Session 数据。

## Client 生命周期

`OpenClientService.create(...)` 创建 `APPLICATION Subject`、`open` Realm 的 `Account` 和 OpenClient，并复制创建者当前的 Principal 关联。Client 的 Operation 必须来自 `OperationCatalog`，且不能超过创建者当前拥有的权限。

Client 停用后不能签发新 Token，也不能调用已经发布的 OpenAPI 路由或 GraphQL 字段。Client 的 Operation 授权是协议无关的；具体应用是否发布某个 Operation 由 OpenAPI 路由或 GraphQL Schema 决定。

## 请求检查

一次开放调用依次经过：

1. 协议适配层恢复 Bearer、HMAC 或其他凭据；
2. `OpenClientOperationChecker` 确认 APPLICATION Client、Client 状态和 Operation 授权；
3. Core 调用生命周期选择 Principal；
4. `@RequirePermission` 检查业务权限和资源范围；
5. 具体应用 Service 执行业务动作。

协议适配层不读取另一个协议的 Controller、Resolver 或 Repository，也不通过 HTTP 内部调用另一个协议。
