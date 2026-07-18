# 第一次运行

完成以下步骤后，PostgreSQL、Redis、RabbitMQ 和 MinIO 会在本机运行，后端会执行数据库迁移，并可使用管理员账号登录管理端。

## 准备开发环境

需要 JDK 21、Docker 与 Docker Compose、Git，以及管理端所需的 Node.js 24 和 pnpm。仓库包含 Gradle Wrapper，后端不要求全局安装 Gradle。

```bash
java -version
docker compose version
./gradlew --version
node --version
pnpm --version
```

复制 `.env.example` 为本机使用的 `.env`，只在本机文件或受控密钥系统保存密码。不要提交真实凭据。

## 启动外部依赖

```bash
docker compose config
docker compose up -d
docker compose ps
```

默认 Compose 提供 PostgreSQL、Redis、RabbitMQ 和 MinIO。具体端口以 `.env.example` 和 `docker-compose.yml` 为准，不以文档示例覆盖真实配置。

## 初始化数据库

主应用启动时由 Flyway 按版本执行 `sca4d-server/src/main/resources/db/migration`。全新环境应使用空 schema。若本机历史迁移校验与当前分支不一致，先确认数据是否可丢弃；开发库可新建干净 schema，生产环境不得修改历史记录或盲目 repair。

## 初始化首个管理员

设置以下变量后首次启动：

```properties
SCA4DJ_BOOTSTRAP_PLATFORM_ADMINISTRATOR_IDENTIFIER=admin
SCA4DJ_BOOTSTRAP_PLATFORM_ADMINISTRATOR_PASSWORD=change-this-password
SCA4DJ_BOOTSTRAP_PLATFORM_ADMINISTRATOR_DISPLAY_NAME=平台管理员
```

只有系统不存在 PLATFORM Principal 且标识和密码都存在时才创建。仓库没有默认密码，也不会覆盖、重新创建或重新启用已有身份。创建完成后从部署环境移除明文初始密码。

## 启动后端

```bash
./gradlew :sca4d-server:bootRun
```

观察 Banner 构建信息、Flyway 结果、BootCheck 摘要和最终监听端口。默认端口为 `8080`，可由 `SCA4DJ_SERVER_PORT` 覆盖。

## 启动管理端

```bash
cd sca4d-admin
pnpm install
pnpm dev
```

管理端必须连接真实后端契约完成首次验证。接口生成和代理配置见[管理端开发](/development/frontend.md)。

## 可选启动网关

只有需要验证路由、健康探测或 upstream 热更新时才启动：

```bash
./gradlew :sca4d-gateway:bootRun
```

主应用不依赖网关才能完成认证和业务处理。网关配置见[可选网关](/infrastructure/gateway.md)。

## 完成运行验收

至少验证：

1. Actuator 健康端点可访问且关键依赖状态符合预期；
2. 初始管理员能够登录；
3. `GET /me` 返回 Subject、固定 Account、Session、当前 Principal 和权限快照；
4. 一个受保护的管理页面或接口可访问；
5. 日志中的 `traceId` 能关联失败响应与访问记录。

失败时记录命令、Profile、响应、日志和数据库迁移状态，再进入[故障排查](/operations/troubleshooting.md)。
