# 配置、部署与运维

## 操作顺序

1. [环境划分](./environments.md)
2. [配置组织与覆盖](./configuration.md)
3. [本地依赖](./local-dependencies.md)
4. [构建与产物](./build-and-package.md)
5. [部署](./deployment.md)
6. [启动、健康与观测](./startup-and-health.md)
7. [日志与 Trace ID](./logging-and-tracing.md)
8. [故障排查](./troubleshooting.md)

## 生产环境检查项

- 只启用一个环境 Profile；
- 由部署平台注入密码和密钥，不把它们写入仓库或日志；
- 数据库迁移、后端 JAR 和管理端产物使用同一提交构建；
- 使用 readiness 决定实例是否接收流量，使用 liveness 判断进程是否需要重启；
- 在响应、访问日志、应用日志、操作日志和消息中传递同一个 traceId；
- 外部服务不可用时保留健康检查结果，不通过关闭检查显示虚假的正常状态。
