# 模块目录

## 根工程

根 `settings.gradle.kts` 包含 `sca4d-server` 与 `sca4d-gateway`。`build.gradle.kts` 配置 Java 21、插件版本、构建信息和 `.env.example` 检查。根工程不包含业务源码。

## sca4d-server

主应用入口为 `com.sca4d.Sca4dApplication`。主要包：

| 包 | 内容 |
| --- | --- |
| `common` | Web、认证、权限、Access、并发、消息、实时、缓存等公共契约 |
| `config` | Spring 装配与类型化 Properties |
| `authentication` | 登录、Session、凭据和 `/me` |
| `iam`、`permission` | Subject、Account、Credential、Principal 和 RBAC 实现 |
| `user`、`tenant`、`role` | 用户、租户、成员和角色管理 |
| `system` | 字典、菜单、操作日志和系统权限目录 |
| `mail` | SMTP 配置、敏感信息加密、连接测试和邮件发送 |
| `file` | 文件存储、签名、Grant 和生命周期 |
| `notification` | 通知创建、查询、已读与实时刷新 |
| `im` | 会话、成员、消息、文件与实时投递 |
| `schedule` | Quartz 调度、Schedule 定义、执行记录和通知任务 |
| `controllers` | 公共、产品端和管理端 HTTP 入口 |
| `entity`、`mapper` | 数据库对象、Mapper 与 XML 映射 |

## common

`common` 保存跨领域接口和基础实现，不依赖具体业务模块。子包包括 `access`、`audit`、`auth`、`cache`、`concurrent`、`dictionary`、`enums`、`exception`、`i18n`、`id`、`messaging`、`permission`、`realtime`、`request`、`resource`、`validation` 和 `web`。

## 身份与权限实现

`authentication` 处理登录入口和会话；`iam` 保存身份、主体、凭据和角色关系；`permission` 将角色权限接入 Sa-Token；领域访问范围与 AccessPolicy 留在所属业务包。当前 Principal 不作为 Session 可变状态。

## 业务模块

用户、租户、成员、角色、系统目录、文件、通知、IM 和 Schedule 都位于主应用中。每个业务包内包含所需的 Service 和 Repository，Mapper 位于 `com.sca4d.mapper`，不为这些业务分别创建 Gradle 模块。

## Controller 入口

- `controllers`：公共能力；
- `controllers.biz.<module>`：产品/业务用户流程；
- `controllers.mg.<module>`：管理流程；
- `controllers.mg.platform`、`mg.tenant`、`mg.system` 等按工作上下文和业务流程细分。

## sca4d-gateway

独立可选应用，包 `config` 保存路由配置，`discovery` 保存 upstream 加载、快照、健康状态和 DiscoveryClient。它不进入主应用事务或权限链。

## sca4d-admin

`common` 是纯 TypeScript API 契约和客户端，可独立被其他前端复用；`app` 是 Vue 3、Pinia、Vue Router、Ant Design Vue 实现，包含 layouts、pages、stores、router、i18n 和 E2E。

## 资源与测试

迁移在 `sca4d-server/src/main/resources/db/migration`，MyBatis XML 在 `resources/mapper`，国际化在 `resources/i18n/*.properties`，后端测试在 `src/test/java`。公共 Java 类型见[接口与注解](./interfaces-annotations.md)。
