# 系统结构

## 运行组件

```text
浏览器或客户端
      │
      ├── HTTP / SSE / WebSocket
      ▼
可选 Gateway
      │
      ▼
sca4d-server（可以运行多个实例）
      ├── PostgreSQL
      ├── Redis
      ├── RabbitMQ
      └── MinIO 或本地文件目录
```

`sca4d-server` 处理登录、权限、业务请求、数据库事务、消息、文件、通知和即时通信。`sca4d-gateway` 只负责转发请求和选择健康实例。`sca4d-admin` 调用主应用提供的 HTTP、SSE 和 WebSocket 接口。

## HTTP 调用过程

```text
Filter
  → 认证拦截器
  → Principal 选择
  → 权限和数据访问检查
  → Controller
  → Service
  → Repository
  → Mapper
  → PostgreSQL
```

响应从 Service 返回业务对象，在 Controller 中转换为 View，再封装为 `ApiResponse<T>`。

## 详细说明

- [应用、网关、管理端和外部服务](./application-boundaries.md)
- [Controller、Service、Repository 和 Mapper](./layers-and-request-flow.md)
- [IAM 身份与访问模型](./iam.md)
- [认证过程与 MFA](./authentication-and-mfa.md)
- [Session、Principal 选择与请求身份](./identity-and-session.md)
- [CurrentRequest](./current-request.md)
- [权限和数据访问范围](./permission-and-access.md)
- [事务、乐观锁和数据删除](./data-and-consistency.md)
- [多实例运行](./runtime-topology.md)
