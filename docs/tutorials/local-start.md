# 第一次运行

先复制本地配置并启动基础设施，再分别启动 Gateway、Server 和管理端，最后用初始管理员登录。Gateway 是浏览器访问后端的入口，Server 执行业务，管理端负责页面；除特别说明外，命令均在仓库根目录执行。

完整示例使用 PostgreSQL、Redis、RabbitMQ、MinIO、Mailpit、EMQX 和 TimescaleDB。Gateway、Server 和管理端需要分别保持运行；按以下顺序启动，每一步检查通过后再继续。

## 准备工具

安装以下工具并确认版本：

- JDK 21；
- Node.js `^22.18.0` 或 `^24.0.0`；
- pnpm 10 或更高版本；管理端工作区当前声明 `pnpm@11.1.2`；
- Docker 与 Docker Compose。

```bash
java -version
node --version
pnpm --version
docker compose version
```

## 创建本地配置

`apps/sca4d-server/src/main/resources/application-local.yml` 会导入仓库根目录 `.env/` 下按用途拆分的 `.env` 文件。下面的循环只复制缺少的文件，不覆盖已经调整过的本地配置：

```bash
for example in .env/*.env.example; do
  target=${example%.example}
  if [ ! -e "$target" ]; then
    cp "$example" "$target"
    printf 'created %s\n' "$target"
  fi
done
```

本地初始管理员用户名来自 `.env/iam.env` 的 `SCA4DJ_IAM_BOOTSTRAP_USERNAME`，初始密码来自 `.env/auth.env` 的 `SCA4DJ_AUTH_PASSWORD_BOOTSTRAP_PASSWORD`。示例密码只用于本机开发；共享或生产环境必须使用外部秘密配置并替换示例值。

`.env/application.env` 中的 `SCA4DJ_BOOTSTRAP_ENABLED=true` 允许首次启动初始化系统设置。产品名称、认证有效期和文件限制使用各自的 `_INIT=true` 标记，仅在数据库中尚未保存该设置时写入；管理端修改后的值不会被重启覆盖。需要修改产品名称时，使用 `SCA4DJ_PRODUCT_DISPLAY_NAME`、`SCA4DJ_PRODUCT_CONTACT_NAME` 和 `SCA4DJ_PRODUCT_TOTP_ISSUER`。数据库连接、Gateway 注册 Secret 和存储凭据仍属于运行配置。

`.env/application.env` 中的 `SCA4DJ_FRONTEND_ORIGIN` 和 `SCA4DJ_GATEWAY_ORIGIN` 分别表示浏览器访问管理端与 Gateway 的完整 Origin。Passkey、Web Realtime 和 MinIO CORS 引用前端地址，Storage 文件传输与联邦登录回调默认使用 Gateway 的 `/api` 入口。需要让某项功能使用不同地址时，再到对应环境文件覆盖它自己的变量。

管理端开发服务器默认使用 `http://localhost:5666`，Gateway 默认使用 `http://localhost:8088`，Server 默认使用 `http://localhost:8080`。本地 `SCA4DJ_AUTH_PASSKEY_RELYING_PARTY_ID` 为 `localhost`；Passkey 必须从 `SCA4DJ_FRONTEND_ORIGIN` 对应的完整地址打开页面，不要混用 `127.0.0.1` 或其他端口。

PostgreSQL 和 TimescaleDB 只在各自数据目录首次初始化时读取用户名与密码。清理数据目录会删除其中的全部数据库数据，只能在确认数据可丢弃或已经备份后执行。要按新的 `.env/database.env` 或 `.env/iot-database.env` 重建本地数据库，先停止对应容器，再由操作者清理 `docker-data/postgresql` 与可选的 `docker-data/iot/timescaledb`；仅修改环境文件不会修改已经存在数据库角色的密码。

Sca4d Server 主库依次执行 `V1__init_sca4d.sql` 和 `V2__unify_retry_rules_and_handler_delivery.sql`。空数据库可以直接初始化；已经执行 V1 的主库先备份，再由 V2 转换 Job 重试规则和 Messaging Outbox，保留原尝试进度与消息标识。更早版本的 Flyway 记录不能直接启动，也不要通过 `repair` 或 `baseline` 跳过校验。独立 TimescaleDB 仍仅通过 V1 建立现行历史数据结构，必须使用空数据库。Docker 数据采用宿主机目录挂载，删除容器或执行 `down --volumes` 都不会清空这些目录；完全重新体验还需由操作者清理可丢弃的 Redis、RabbitMQ、MinIO 和 EMQX 状态，避免遗留会话、消息和文件。RabbitMQ 初始密码也只在首次初始化时写入。

## 启动全部本地依赖

完整环境包含 PostgreSQL、Redis、RabbitMQ、MinIO、Mailpit、EMQX 和 TimescaleDB。`.env/iot.env.example` 与 `.env/iot-database.env.example` 默认启用后两项，因此体验全部示例功能时必须同时加载两个 Compose 文件。Docker Compose 不会自动读取 `.env/` 目录中的分片文件，必须显式传入参与 Compose 变量替换的文件：

```bash
docker compose \
  --env-file .env/application.env \
  --env-file .env/database.env \
  --env-file .env/redis.env \
  --env-file .env/rabbitmq.env \
  --env-file .env/storage.env \
  --env-file .env/notification.env \
  --env-file .env/iot.env \
  --env-file .env/iot-database.env \
  -f docker-compose.yml \
  -f docker-compose.iot.yml \
  up -d

docker compose \
  --env-file .env/application.env \
  --env-file .env/database.env \
  --env-file .env/redis.env \
  --env-file .env/rabbitmq.env \
  --env-file .env/storage.env \
  --env-file .env/notification.env \
  --env-file .env/iot.env \
  --env-file .env/iot-database.env \
  -f docker-compose.yml \
  -f docker-compose.iot.yml \
  ps
```

这一步只确认容器状态，不代表 Sca4d Server 已经启动。`ps` 还必须显示 `iot-emqx` 与 `iot-timescaledb`；Mailpit 的 SMTP 地址为 `localhost:1025`，收件箱界面为 `http://localhost:8025`，它只用于本机查看测试邮件。

## 启动 Gateway

Gateway 的 `application-local.yml` 自动读取仓库根目录 `.env/application.env` 和 `.env/logging.env`。前者提供与 Server 相同的注册 Secret，后者在本机启用集中日志时提供日志写入路由配置：

只启动基础设施和 IoT Compose 时，将 `.env/logging.env` 的 `SCA4DJ_GATEWAY_LOGGING_ENABLED` 设为 `false`。集中日志需要另行启动日志 Compose，并配置对应的 Loki 凭据。

```bash
./gradlew :gateway:sca4d-gateway:bootRun
```

Gateway 默认监听 `http://localhost:8088`。打开 `http://localhost:8088/actuator/health`，状态应为 `UP`。此时尚未启动 Server，访问 `/api/**` 返回 503 是预期结果。

## 启动 Server

另开一个终端：

```bash
./gradlew :apps:sca4d-server:bootRun
```

`bootRun` 会自动构建 MFA 插件并设置 `SCA4DJ_MFA_PLUGIN`。在 IDEA 中直接运行 `Sca4dApplication` 时，先执行 `./gradlew :apps:sca4d-server-mfa-plugin:pluginJar`，再把 `.env/application.env` 中的 `SCA4DJ_MFA_PLUGIN` 设为生成文件 `apps/sca4d-server-mfa-plugin/build/libs/sca4d-server-mfa-plugin-1.0.0-plugin.jar` 的绝对路径；IDE 直接运行不会执行 `bootRun` 的构建和环境配置。工作目录使用仓库根目录或 `apps/sca4d-server`，本地配置都能从 `.env/` 导入。

Server 默认监听 `http://localhost:8080`，readiness 后使用 `.env/application.env` 中相同的 Secret 向 Gateway 注册。`local` 配置在 `SCA4DJ_CORE_OPENAPI_ENABLED=true` 时启用 OpenAPI 和 Swagger UI，可从统一入口打开：

- `http://localhost:8088/api/swagger-ui/index.html`；
- `http://localhost:8088/api/v3/api-docs`。

本地运行默认保留业务 DEBUG 日志，关闭应用 TRACE 和 MyBatis SQL 输出；定时扫描不会逐次打印 SQL、参数和结果。

Server 启动日志出现「Server 实例已向 Gateway 注册」后，再使用 Swagger UI 和登录请求确认统一入口已经可用。`http://localhost:8080` 只用于检查单个 Server 实例，不作为浏览器和外部系统的正常入口。

## 启动管理端

另开一个终端。开发服务器自动读取仓库根目录 `.env/frontend.env` 中的 `SCA4DJ_API_TARGET`，修改目标地址后需要重启开发服务器：

```bash
cd frontends/sca4d-admin
pnpm install
pnpm dev
```

开发脚本默认监听 `http://localhost:5666`。浏览器请求使用 `/api`，Vite 保留路径并转发到 `SCA4DJ_API_TARGET`；环境示例默认指向 `http://localhost:8088`，端口被占用时应与 `.env/application.env` 中的 Gateway 地址保持一致。

管理端 API 基础路径使用 `VITE_GLOB_API_URL=/api`。`.env/frontend.env` 由 Node 读取，变量值应填写完整地址，不使用 `${其他变量}` 引用。

## 配置本地邮件

清空数据库后没有 SMTP 发送方或 Email 模板。登录初始管理员后，通过 Swagger UI 或管理端调用 `/system/email/senders` 创建一个 `host=localhost`、`port=1025`、`security=NONE` 且不带用户名和密码的 Mailpit 发送方，测试连接并启用它。再为实际要发送的通知 `TreeCode` 创建并启用 Email 模板；这两类资源保存在数据库，不应写入环境变量。认证邮件会出现在 `http://localhost:8025`。

## 检查结果

1. 完整环境的 Compose 服务已经运行，包含 `iot-emqx` 和 `iot-timescaledb`；
2. Gateway health 为 `UP`，Server 日志显示应用监听 8080 并成功注册；
3. Swagger UI 能通过 Gateway 的 `/api` 入口打开；
4. 管理端能够在 `http://localhost:5666` 打开登录页，并通过 `/api` 请求后端；
5. 上传头像或文件时，浏览器能够向 MinIO 的预签名地址传输文件；
6. 注册 Passkey 后，浏览器能够完成 WebAuthn ceremony 并提交结果；
7. 启用的 Mailpit SMTP 发送方和 Email 模板能够把认证邮件显示在 Mailpit 收件箱。

接下来按 [HTTP API 基础](/tutorials/authentication-and-session) 完成登录。运行配置的准确入口见 [Sca4d Server 应用规格](/reference/)，前后端职责见 [Sca4d Server 应用设计](/tutorials/assemble-application) 与 [Sca4d 管理端设计](/tutorials/admin-feature)。
