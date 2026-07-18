# 部署

## 主应用部署

每个实例使用 Java 21，挂载或注入相同业务配置，并为 `SCA4DJ_ID_WORKER_ID`/`DATA_CENTER_ID` 规划唯一组合。实例共享 PostgreSQL、Redis、RabbitMQ 和 MinIO，业务文件不能只保存在某个实例的本地磁盘。

## 多实例部署

先部署一个新实例完成迁移兼容和 readiness，再逐步加入流量。发布期间新旧版本必须在计划窗口内保持数据库契约兼容；不通过长期源码兼容掩盖未经设计的滚动升级。

## 网关部署

网关是独立 JAR。维护 upstream 文件，新增实例先等待 readiness 健康；下线时设置 `draining: true`，停止新流量后等待在途请求再终止。

## 管理端部署

管理端静态资源部署在 Web Server/CDN，API base URL、WebSocket endpoint、context path 和 Origin 策略必须一致。不得把后端秘密编译进前端环境变量。

## 可信代理

代理部署使用 `SERVER_FORWARD_HEADERS_STRATEGY=NATIVE`，并将 Tomcat internal proxies 收紧到实际 Gateway/Ingress 网段。公网直连使用 `NONE`。应用不直接相信客户端转发头。

## 数据库迁移时机

明确由应用启动或独立发布步骤执行 Flyway，避免多个不同版本同时迁移。变更先保证 schema 对当前发布序列可用，再上线应用；生产禁止修改历史迁移。

## 优雅停机

先从 readiness/upstream 移除流量，等待 HTTP、Quartz Job、消息发送、Outbox claim 和异步审计完成，再终止。`SCA4DJ_SCHEDULER_SHUTDOWN_WAIT` 必须小于编排平台的 grace period，并为其他关闭步骤留出时间。

## 发布回滚

回滚前判断数据库迁移是否向后兼容。应用 JAR、管理端和配置作为一组回滚；不可逆数据变化需要前滚修复方案，不能假设替换旧 JAR 自动恢复 schema。
