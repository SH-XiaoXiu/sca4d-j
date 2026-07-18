# 应用、网关和外部服务

## sca4d-server

`sca4d-server` 包含 HTTP 接口、业务 Service、Repository、Mapper、数据库迁移和后台任务。一次业务操作可以在一个进程和一个数据库事务中完成。

可以同时运行多个 `sca4d-server` 实例。所有实例连接同一 PostgreSQL、Redis、RabbitMQ 和文件存储。

## 可选网关

`sca4d-gateway` 读取 upstream 文件，检查主应用实例的 readiness，并把请求转发到健康实例。它不读取业务数据库，也不执行登录和权限检查。主应用可以不经过 Gateway 直接提供服务。

## 管理端

`sca4d-admin/common` 包含 TypeScript 请求类型、响应类型和 HTTP Client。`sca4d-admin/app` 包含 Vue 路由、Pinia store、布局和页面。`pnpm api:generate` 根据后端 OpenAPI 更新生成类型。

## PostgreSQL、Redis、RabbitMQ 和文件存储

- PostgreSQL 保存业务记录、权限关系、操作日志和 Outbox；
- Redis 保存 Session、权限缓存、普通缓存、幂等记录、锁和限流桶；
- RabbitMQ 传递业务消息、SSE 广播和 STOMP 消息；
- MinIO 或本地目录保存文件内容，业务表保存对应的 `file_id`；
- 代理或网关负责转发，主应用只信任容器解析后的客户端地址。

外部服务的启动检查和失败结果见[启动、健康与观测](/operations/startup-and-health.md)。
