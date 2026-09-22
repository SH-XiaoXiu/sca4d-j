# Sca4d-J

Sca4d-J（Scaffold for Java）是一套 Java 项目模板，适合需要管理端、账号权限和后端业务接口的项目。模板包含可运行的 Sca4d Server、Gateway 和管理端，以及身份认证、文件、消息、任务、实时通信和 IoT 模块。开发者可以按业务需要保留、删除或扩展这些功能。

Sca4d-J 采用[分布式单体](/home/architecture)：业务体系作为完整应用开发和运行，可以部署一个或多个实例。单实例与集群使用同一套业务实现，认证、权限、并发和数据约束保持一致。

第一次使用，从[本地启动](/tutorials/local-start)运行应用并登录管理端，再按[组装应用](/tutorials/assemble-application)选择所需模块。代码放置位置见[项目结构](/home/project-structure)。

## 开发业务功能

新增功能通常从[后端业务接口](/tutorials/backend-feature)开始，再接入[管理端页面](/tutorials/admin-feature)。身份建模先区分[接入者、账号和业务身份](/home/identity)，接口与数据权限使用[Resource、Action 和 Permission](/home/permission)。登录和敏感操作验证见[认证与 Session](/home/authentication-session)。

编写和评审代码遵守[项目规范](/standards/)。查路由、配置或 Java 方法时使用[参考](/reference/)；定义具体业务规则时使用[需求与实现设计](/requirements/)；协作任务的准备和验收见[AI 协作](/ai-collaboration/)。

## 内置功能

| 功能 | 主要项目 | 使用入口 |
|---|---|---|
| 身份、账号与授权 | `sca4dj-iam` | [接入身份与权限](/tutorials/identity-and-permission) |
| Password、TOTP、Passkey、验证码与联邦登录 | `sca4dj-auth-*` | [配置认证与会话](/tutorials/authentication-and-session) |
| 字典、设置、并发与敏感值 | `sca4dj-core-*` 与 App | [定义设置与字典](/tutorials/settings-and-dictionaries) |
| 业务消息、Outbox 与通知 | `sca4dj-messaging`、`sca4dj-notification-*` | [发布消息与通知](/tutorials/messaging-and-notification) |
| 文件生命周期和存储 | `sca4dj-storage-*` | [完成文件生命周期](/tutorials/file-lifecycle) |
| 任务、处理管线和 Quartz 调度 | `sca4dj-job`、`sca4dj-scheduling-quartz` | [创建任务与调度](/tutorials/jobs-and-scheduling) |
| SSE、WebSocket、STOMP 与多节点投递 | `sca4dj-web-realtime` | [推送实时事件](/tutorials/realtime-events) |
| 开放 API、Client Credentials、HMAC 与 JWT | `sca4dj-openapi` | [开放外部 API](/tutorials/external-api) |
| Product、Thing Model、Device、状态与命令 | `sca4dj-iot-*` | [IoT 对象模型](/home/iot) |

## 技术选择

Sca4d-J 使用 Java 21、Spring Boot 3.5、PostgreSQL 17、Redis 7、RabbitMQ 4、Gradle 和 Vue 3。PostgreSQL、Redis 和 RabbitMQ 是应用的固定基础设施。Vben 是默认管理端示例，可以整体替换；业务规则和最终权限判断留在后端。

单机与集群的部署条件见[架构与运行](/home/architecture)，环境变量和默认值见[运行配置](/reference/runtime/sca4d-server)。
