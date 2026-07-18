# 从模板创建业务项目

## 前置条件

- 已完成[第一次运行](./getting-started.md)，后端和管理端可以启动。
- 已创建独立 Git 分支或新仓库。
- 已确定项目名称、部署环境和需要保留的组件。

## 1. 修改项目名称

检查并替换：

- `settings.gradle.kts` 中的 `rootProject.name`；
- `SCA4DJ_APPLICATION_NAME` 和 `SCA4DJ_GATEWAY_APPLICATION_NAME`；
- README、文档首页和管理端标题；
- JAR、镜像、部署单元和日志服务名称。

Java 包名 `com.sca4d` 是否修改由项目决定。修改时使用 IDE 重构，并运行全部后端测试。

## 2. 配置开发环境

从 `.env.example` 复制本机所需配置到未提交的 `.env`。至少修改数据库、RabbitMQ、MinIO 和初始管理员密码。

```bash
docker compose config
docker compose up -d
./gradlew checkEnvExample
./gradlew :sca4d-server:bootRun
```

首次管理员创建成功后，从部署配置中删除明文初始密码。

## 3. 选择部署组件

主应用 `sca4d-server` 必须保留。根据项目需要选择：

- `sca4d-gateway`：需要文件配置的 upstream、主动健康检查或统一转发地址时使用；
- `sca4d-admin`：需要内置管理端时使用；
- RabbitMQ：消息、Outbox 跨节点发送、SSE 广播或 STOMP relay 启用时使用；
- MinIO：多实例共享文件时使用；单机开发可以使用 `LOCAL`。

停用某项功能时，根据配置参考关闭对应开关，并运行应用启动测试和健康检查。

## 4. 创建第一个业务模块

按照[创建一个业务功能](/development/feature-workflow.md)添加迁移、Entity、Mapper、Repository、Service、Controller、权限、管理页面和测试。

公告示例只存在于文档中，不需要从源码删除。可以直接用真实业务名称创建新模块。

## 5. 填写部署配置

确认以下配置已经由部署平台提供：

- PostgreSQL、Redis、RabbitMQ 和 MinIO 地址及凭据；
- 文件 Grant 签名密钥；
- 每个实例不同的 worker ID 和 data center ID；
- Gateway upstream 文件；
- 可信代理地址和 WebSocket 允许的 Origin；
- 日志保存、健康检查和应用关闭等待时间。

## 6. 完成首次运行检查

- `/actuator/health/readiness` 返回可接收流量；
- 管理员可以登录；
- `GET /me?principalType=PLATFORM` 返回平台 Principal 和权限；
- 管理端可以查询用户、租户、角色、字典和菜单；
- 上传并读取一个测试文件；
- 后端、管理端和文档构建成功。

## 失败处理

重命名后无法构建时，搜索旧项目名、Java 包名和环境变量前缀。应用无法启动时先检查 Profile、数据库迁移和外部服务地址。管理端接口不匹配时启动当前后端，再执行 `pnpm api:generate` 和 `pnpm api:check`。
