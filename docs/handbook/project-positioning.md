# 项目定位

Sca4d-J（Scaffold for Java）是一个可直接运行的 Java 后端工程模板。它以 Java 21、Spring Boot 3 和 PostgreSQL 为基础，包含项目结构、数据库迁移、测试、构建和文档，业务项目可以按需使用其中的内置模块。

## 已包含的软件

| 部分 | 技术 |
| --- | --- |
| 主应用 | Java 21、Spring Boot 3、MyBatis-Flex、Sa-Token |
| 数据库 | PostgreSQL、Flyway |
| 缓存和并发控制 | Redis、Redisson、Bucket4j |
| 消息 | RabbitMQ、数据库 Outbox |
| 调度 | Quartz JDBC JobStore |
| 文件 | MinIO 或本地目录 |
| 管理端 | TypeScript、Vue 3、Pinia、Vue Router、Ant Design Vue |
| 可选网关 | Spring Cloud Gateway |

## 已包含的业务功能

- 密码登录、退出、Session 查询和踢出；
- Subject、Account、Credential、平台 Principal 和租户 Principal；
- 用户、租户、成员、角色、权限和平台租户 ACL；
- 系统字典、菜单和操作日志；
- 文件上传、读取签名和过期文件清理；
- 通知列表、未读数量和已读操作；
- 单聊、群聊、文本消息、图片消息和已读位置；
- SSE、WebSocket 和 STOMP；
- 乐观锁、幂等请求、分布式锁和限流；
- Quartz 集群调度、系统清理 Job 和通知 Schedule。

## 适用场景

- 使用 Java 21、Spring Boot 3 和 PostgreSQL 开发后端服务；
- 希望从可运行的工程开始，并复用现成的目录结构、数据库迁移、测试、构建和项目文档；
- 默认采用模块化单体组织业务，并根据实际容量以单实例或多实例方式运行；
- 需要时选用身份权限、管理端、租户、Gateway、RabbitMQ、MinIO、实时通信或调度等能力；
- 希望保留清晰的模块边界，并允许业务项目按自身需求关闭、替换或扩展可选模块。

## 不包含的功能

- 服务注册中心和微服务调用框架；
- 动态插件安装；
- 低代码页面生成；
- 面向第三方开发者的长期兼容 SDK；
- Kubernetes、云数据库和消息服务的部署模板。

这些功能可以由业务项目根据部署环境添加，但不属于当前仓库。

需要判断项目是否应采用该脚手架、是否应直接选择微服务，或何时增加 PrincipalType、领域 ACL 和 API 根路径分类时，查看[脚手架选型与扩展参考](./evaluation-criteria.md)。

## 公告示例

`docs/development/example/announcement` 包含公告业务的数据库迁移、Repository、Service、Controller、权限、管理页面和测试代码。仓库当前不包含公告业务源码，示例中的类需要按步骤创建。
