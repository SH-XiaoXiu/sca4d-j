<h1 align="center">Sca4d-J</h1>

> [!WARNING]
> Sca4d-J 处于 Alpha 阶段，功能仍在开发中。

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot 3.5">
  <img src="https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white" alt="PostgreSQL 17">
  <img src="https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white" alt="Redis 7">
  <img src="https://img.shields.io/badge/RabbitMQ-4-FF6600?logo=rabbitmq&logoColor=white" alt="RabbitMQ 4">
  <img src="https://img.shields.io/badge/Vue-3-4FC08D?logo=vuedotjs&logoColor=white" alt="Vue 3">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-2F855A" alt="MIT License"></a>
</p>

<p align="center">
  <a href="docs/tutorials/local-start.md">教程</a> ·
  <a href="docs/standards/principles.md">项目规范</a> ·
  <a href="docs/reference/index.md">参考</a> ·
  <a href="docs/requirements/index.md">需求文档</a>
</p>

Sca4d-J（Scaffold for Java）是一套可直接运行、可按业务需要删减的 Java 项目模板，包含身份、认证、文件、消息、任务等基础功能，以及数据库迁移、测试和构建配置。开发者可以选择需要的模块，在此基础上编写业务代码。

## 架构与运行

Sca4d-J 采用分布式单体架构：一个 App 包含完整业务体系，不拆成微服务；同一套实现既能运行在单实例上，也能运行在集群中。Session、锁、任务、消息、连接索引和失败恢复按多实例设计，单体应用同样支持高可用部署。

后端基于 Java 21 和 Spring Boot 3.5。所有 App 都需要 PostgreSQL 17、Redis 7 和 RabbitMQ 4。Messaging 使用 Outbox 保证业务消息发布，由 RabbitMQ 持久保存；Web Realtime 向在线浏览器发送允许丢失的 SSE 或 STOMP 通知。

| 目录 | 职责 |
|---|---|
| `core/` | 公共接口和基础类型 |
| `modules/` | 身份、认证、多因素认证（MFA）、业务消息、Web Realtime 等功能 |
| `apps/` | 选择模块、配置并启动具体应用 |
| `frontends/` | 管理端页面和前端公共代码 |
| `gateway/` | API、SSE、WebSocket 和回调的独立负载均衡入口 |

## 开始使用

按[本地启动教程](docs/tutorials/local-start.md)准备环境并运行应用。添加业务功能时，从[开发后端业务接口](docs/tutorials/backend-feature.md)开始。

HTTP 契约、运行配置和 Java API 见[参考](docs/reference/index.md)。修改源码前阅读[项目规范](docs/standards/principles.md)。

## 构建文档

在仓库根目录运行 `./gradlew documentationSite`。生成的站点位于 `build/docs/site/`，包含文档页面和按 Gradle 项目路径组织的 Javadoc。

## License

使用 [MIT License](LICENSE)。
