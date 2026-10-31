# GraphQL 外部调用

GraphQL 外部调用由 `modules/open/sca4dj-open-graphql` 提供。具体应用声明自己的 `.graphqls` Schema 和 Resolver，开放模块只负责 Bearer 认证、Client Operation 检查、GraphQL 错误转换和查询限制。

使用 GraphQL Starter 后，HTTP 入口默认为 `POST /graphql`。Spring Boot 通过 `spring-boot-starter-graphql` 接入 Spring MVC；具体应用仍需在 Schema 中明确公开字段。

## 声明开放字段

开放模块提供 `@openOperation` Schema 指令：

```graphql
directive @openOperation(operation: String!) on FIELD_DEFINITION

type Query {
    device(deviceId: ID!): Device
        @openOperation(operation: "IOT:DEVICES:READ")
}
```

`operation` 必须存在于 `OperationCatalog`。建议为每个 Query 和 Mutation 根字段声明一个 Operation；未授权的字段不会调用 Resolver。

Resolver 仍由具体应用维护，并继续使用 `@CurrentPrincipal` 和 `@RequirePermission`：

```java
@QueryMapping
@RequirePermission(operation = IotOperations.DEVICES_READ_VALUE)
public Device device(@Argument long deviceId) {
    return devices.find(deviceId);
}
```

`@openOperation` 检查外部程序是否被 Client 授权，`@RequirePermission` 检查当前业务主体是否拥有业务权限。两者都通过后才执行 Resolver。

## 请求限制和错误

GraphQL 模块默认限制查询深度和复杂度，并默认关闭 introspection。具体应用可以使用 `sca4dj.open.graphql.*` 覆盖限制。

认证失败、权限拒绝和业务异常写入 GraphQL `errors[].extensions.code` 与 `errors[].extensions.messageKey`，响应不会包含 Token、Secret、堆栈或 SQL。

GraphQL 初版支持 HTTP Query 和 Mutation，不自动转换 REST Controller，也不支持 Subscription。
