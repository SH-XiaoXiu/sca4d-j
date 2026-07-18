

<h1 align="center">Sca4d-J</h1>

> [!WARNING]
> 当前项目处于实现性阶段（Alpha 版本）。

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot 3.5">
  <img src="https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white" alt="PostgreSQL 17">
  <img src="https://img.shields.io/badge/Vue-3-4FC08D?logo=vuedotjs&logoColor=white" alt="Vue 3">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-2F855A" alt="MIT License"></a>
</p>

<p align="center">
  <a href="https://jdev.ncraft.net/handbook/getting-started">快速开始</a> ·
  <a href="https://jdev.ncraft.net/">在线文档</a> ·
</p>

Sca4d-J 是一个可直接运行的 Java 后端工程模板。它以 Java 21、Spring Boot 3.5 和 PostgreSQL 17 为基础，提供主应用、Vue 管理端、可选网关、本地依赖、测试、构建与项目文档，让业务项目从一套已经跑通的工程结构开始。

它不靠堆积更多设计模式显得高级。项目更在意五个月以后，新人能不能预测功能应该放在哪里、对象应该叫什么、数据应该从哪一层读取，以及改完以后要通过哪些检查。

目标不是替业务项目决定全部技术选型，而是先稳定那些每个项目都会反复遇到的工程问题：身份与权限、数据访问、并发、消息、文件、调度、管理端、测试和交付。

见[作者的话](docs/author-note/index.md)和[核心编码规则](docs/standards/general/core-rules.md)。



目录与代码位置见[工程结构](docs/handbook/project-structure.md)。

## 已经内置好的功能

[内置功能文档](docs/infrastructure/index.md)与[参考手册](docs/reference/index.md)。


准确版本和第三方许可证见[第三方依赖目录](docs/reference/third-party-dependencies.md)。

选型前建议阅读[项目定位](docs/handbook/project-positioning.md)和[选型与扩展参考](docs/handbook/evaluation-criteria.md)。

## 快速开始

初始管理员、数据库迁移、可选 Gateway 和运行验收见[第一次运行](docs/handbook/getting-started.md)。完整默认值和部署预期以[配置参考](docs/reference/configuration.md)为准。


## License

使用 [MIT License](LICENSE)。
