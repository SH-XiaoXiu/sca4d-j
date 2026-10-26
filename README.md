<h1 align="center">Sca4d-J</h1>

> [!WARNING]
> 项目处于 Alpha 阶段，功能仍在开发中，接口和目录结构可能调整。

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
  <a href="docs/standards/index.md">项目规范</a> ·
  <a href="docs/reference/index.md">参考</a> ·
  <a href="docs/requirements/index.md">需求文档</a>
</p>

Sca4d-J（Scaffold for Java）是一套可直接运行、可按需删减的 Java
项目模板。它提供身份、认证、文件、消息、任务等基础功能，并包含数据库迁移、测试和构建配置。业务项目可以保留需要的模块、删除或替换其余模块，再在此基础上编写业务代码。

## 快速开始

- 按[本地启动教程](docs/tutorials/local-start.md)准备环境并运行应用。
- 添加业务接口时，参照[后端业务接口教程](docs/tutorials/backend-feature.md)。
- HTTP 契约、运行配置和 Java API 见[参考](docs/reference/index.md)。
- 修改源码前阅读[项目规范](docs/standards/index.md)。

## 构建文档

在仓库根目录运行 `./gradlew documentationSite`。生成的站点位于 `build/docs/site/`，包含文档页面，以及按 Gradle 项目路径组织的
Javadoc。

## License

本项目使用 [MIT License](LICENSE)。
