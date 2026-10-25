# 第一次运行

场景：在本机按顺序启动依赖服务、Gateway、Server 和管理端，用初始管理员通过 Gateway 完成第一次登录。

Gateway 是浏览器和外部系统访问后端的入口。Server 执行业务。管理端提供页面。三者各占一个终端并保持运行。

除特别说明外，命令都在仓库根目录执行。每一步检查通过后再继续。

## 实现步骤

### 1. 准备工具

安装以下工具：

- JDK 21；
- Node.js `^22.18.0` 或 `^24.0.0`；
- pnpm 10 或更高版本，管理端工作区声明 `pnpm@11.1.2`；
- Docker 与 Docker Compose。

确认版本：

```bash
java -version
node --version
pnpm --version
docker compose version
```

### 2. 创建本地配置

Server 的 `application-local.yml` 导入仓库根目录 `.env/` 下按用途拆分的文件。下面的循环只复制缺少的文件，不覆盖已经调整过的配置。缺少某个功能时，省略对应文件即可，每个功能的配置键以 `SCA4DJ_` 开头，并由对应模块读取：

```bash
for example in .env/*.env.example; do
  target=${example%.example}
  if [ ! -e "$target" ]; then
    cp "$example" "$target"
    printf 'created %s\n' "$target"
  fi
done
```

检查以下配置：

| 配置 | 文件 | 说明 |
|---|---|---|
| `SCA4DJ_IAM_BOOTSTRAP_USERNAME` | `.env/iam.env` | 初始管理员用户名 |
| `SCA4DJ_AUTH_PASSWORD_BOOTSTRAP_PASSWORD` | `.env/auth.env` | 初始密码，示例值只适用于本机 |
| `SCA4DJ_GATEWAY_SECRET` | `.env/application.env` | Gateway 与 Server 共用的注册 Secret |
| `SCA4DJ_FRONTEND_ORIGIN` | `.env/application.env` | 浏览器访问管理端的完整 Origin，示例为 `http://localhost:5666` |
| `SCA4DJ_GATEWAY_ORIGIN` | `.env/application.env` | 浏览器访问 Gateway 的完整 Origin，示例为 `http://localhost:8088` |

共享或生产环境必须使用外部秘密配置，并替换示例值。

启用 Passkey 模块时，页面必须从 `SCA4DJ_FRONTEND_ORIGIN` 对应的完整地址打开。不要混用 `127.0.0.1`、`localhost` 或其他端口。Passkey 的 `relyingPartyId` 默认为 `localhost`。

Passkey、Web Realtime 和 MinIO CORS 引用前端地址。Storage 文件传输与联邦登录回调默认使用 Gateway 的 `/api` 入口。某项功能需要不同地址时，在对应环境文件中覆盖它自己的变量。

系统设置的初始化条件：

- 条件：初始化节点设置 `SCA4DJ_BOOTSTRAP_ENABLED=true`。
- 动作：节点读取每个设置声明的 `<ENV>` 与 `<ENV>_INIT`，例如产品名称使用 `SCA4DJ_PRODUCT_DISPLAY_NAME`。
- 结果：设置只在数据库中尚未保存时写入。管理端修改后的值不会被重启覆盖。

数据库连接、Gateway 注册 Secret 和存储凭据属于运行配置，不是系统设置。

### 3. 启动依赖服务

完整环境包含 PostgreSQL、Redis、RabbitMQ、MinIO、Mailpit、EMQX 和 TimescaleDB。`.env/iot.env.example` 与 `.env/iot-database.env.example` 默认启用后两项，因此需要同时加载两个 Compose 文件。

Docker Compose 不会自动读取 `.env/` 下的分片文件。启动命令必须显式传入参与变量替换的文件：

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
```

检查容器：

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
  ps
```

输出必须包含 `postgresql`、`redis`、`rabbitmq`、`minio`、`mailpit`、`iot-emqx` 与 `iot-timescaledb`。这一步只确认容器状态，不代表 Server 已经启动。

Mailpit 的 SMTP 地址为 `localhost:1025`，收件箱为 `http://localhost:8025`，只用于本机查看邮件。

### 4. 启动 Gateway

Gateway 的 `application-local.yml` 读取 `.env/application.env` 与 `.env/logging.env`。前者提供与 Server 相同的注册 Secret，后者提供集中日志写入路由配置。

只启动基础设施和 IoT Compose 时，把 `.env/logging.env` 中的 `SCA4DJ_GATEWAY_LOGGING_ENABLED` 保持为 `false`。

```bash
./gradlew :gateway:sca4d-gateway:bootRun
```

检查：

```bash
curl --silent http://localhost:8088/actuator/health
```

状态应为 `UP`。此时 Server 尚未启动，访问 `/api/**` 返回 503 是预期结果。

### 5. 启动 Server

另开一个终端：

```bash
./gradlew :apps:<应用名>-server:bootRun
```

在 IDE 中直接运行启动类时，工作目录使用仓库根目录或 `apps/<应用名>-server`，两者都能导入 `.env/`。

Server 的监听端口由具体应用的 `server.port` 决定。`.env/application.env` 中的 `SCA4DJ_SERVER_BASE_URL` 必须与它一致，Gateway 据此访问 Server。

Server 在 readiness 通过后，使用相同的 Secret 向 Gateway 注册。启动日志出现「Server 实例已向 Gateway 注册」后，再继续。

`SCA4DJ_OPENAPI_ENABLED=true` 时，`local` 配置启用 OpenAPI 与 Swagger UI。统一入口如下：

- `http://localhost:8088/api/swagger-ui/index.html`；
- `http://localhost:8088/api/v3/api-docs`。

直接访问 Server 端口只用于检查单个实例，不作为浏览器和外部系统的入口。

### 6. 启动管理端

另开一个终端：

```bash
cd frontends/<应用名>-admin
pnpm install --frozen-lockfile
pnpm dev
```

开发服务器读取仓库根目录 `.env/frontend.env`，其中指向 Gateway 的目标地址变量以 `_GATEWAY_TARGET` 结尾，示例值为 `http://localhost:8088`。修改目标地址后，重启开发服务器。

默认监听 `http://localhost:5666`。浏览器请求使用 `/api`，Vite 把它转发到 Gateway。端口被占用时，调整后的地址必须同时写入 `SCA4DJ_FRONTEND_ORIGIN`。

`VITE_GLOB_API_URL` 固定为 `/api`。`.env/frontend.env` 由 Node 读取，变量值写完整地址，不使用 `${其他变量}` 引用。

### 7. 登录

打开 `http://localhost:5666`，使用 `.env/iam.env` 与 `.env/auth.env` 中的初始管理员登录。

也可以通过 Gateway 发送请求。密码从终端读取，避免写入命令历史：

```bash
read -r -p 'username: ' USERNAME
read -r -s -p 'password: ' PASSWORD; echo

curl --fail-with-body --silent --show-error \
  -H 'Content-Type: application/json' \
  -d "{\"identifier\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" \
  http://localhost:8088/api/login/attempts
```

响应中 `data.session` 有值时，登录已经建立 Session。为空时，按 `data.authentication` 的进度继续提交 proof。详见[接入认证与 Session](/tutorials/authentication-and-session)。

### 8. 配置本地邮件

清空数据库后，系统中没有 SMTP 发送方，也没有 Email 模板。认证邮件发送前，按以下顺序配置：

1. 登录初始管理员，创建 Mailpit 发送方。
2. 测试连接，并启用该发送方。
3. 为实际要发送的通知 `TreeCode` 创建并启用 Email 模板。

第一步的请求如下，`ACCESS_TOKEN` 取自登录响应：

```bash
curl --fail-with-body --silent --show-error \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "name":"mailpit",
    "host":"localhost",
    "port":1025,
    "security":"NONE",
    "fromAddress":"noreply@example.test"
  }' \
  http://localhost:8088/api/system/email/senders
```

新建的发送方默认停用。测试连接与启用使用 Swagger UI 或管理端完成。发送方和模板保存在数据库中，不写入环境变量。认证邮件显示在 `http://localhost:8025`。

## 验证

1. `docker compose ... ps` 显示完整环境的服务在运行，包含 `iot-emqx` 和 `iot-timescaledb`。
2. Gateway 的 `/actuator/health` 为 `UP`，Server 日志显示已向 Gateway 注册。
3. Swagger UI 能通过 Gateway 的 `/api` 入口打开。
4. 管理端能在 `http://localhost:5666` 打开登录页，并通过 `/api` 请求后端。
5. 登录响应包含 `data.session`，`GET /api/sessions/current` 返回当前 Session。
6. 上传头像或文件时，浏览器能向 MinIO 的预签名地址传输文件。
7. 启用 Passkey 模块时，注册 Passkey 后浏览器能完成 WebAuthn ceremony 并提交结果。
8. 启用的 Mailpit 发送方和 Email 模板能让认证邮件显示在 Mailpit 收件箱。

## 失败结果与限制

| 现象 | 原因与处理 |
|---|---|
| Gateway 访问 `/api/**` 返回 503 | 没有租约有效、readiness 成功的 Server 实例。确认 Server 已启动，且两侧的 `SCA4DJ_GATEWAY_SECRET` 相同。 |
| Gateway 返回 502 | Gateway 已选中实例，但 TCP 连接失败。核对 `SCA4DJ_SERVER_BASE_URL` 的地址和端口。 |
| `docker compose up` 报变量缺失或使用了默认值 | 没有显式传入对应的 `--env-file`。Compose 不读取 `.env/` 下的分片文件。 |
| 修改 `.env/database.env` 后数据库密码没有变化 | PostgreSQL 和 TimescaleDB 只在数据目录首次初始化时读取用户名与密码。RabbitMQ 初始密码同样只在首次初始化时写入。 |
| 启用 Passkey 模块后注册失败 | 页面地址与 `SCA4DJ_FRONTEND_ORIGIN` 不一致，或使用了 `127.0.0.1`。 |
| 管理端请求没有到达 Gateway | `.env/frontend.env` 的 Gateway 目标地址不正确。修改后重启开发服务器。 |
| 认证邮件没有出现在 Mailpit | 没有启用的发送方或对应 `TreeCode` 的模板。 |
| 迁移失败 | 先检查失败的 SQL 和数据库状态。不要使用 `repair` 或 `baseline` 跳过校验，也不要改写已在共享环境执行的迁移。 |

重建本地数据库的条件与步骤：

1. 确认数据可以丢弃，或已经备份。
2. 停止对应容器。
3. 由操作者清理 `docker-data/postgresql`，以及可选的 `docker-data/iot/timescaledb`。

清理会删除目录中的全部数据库数据。仅修改环境文件，不会修改已经存在的数据库角色的密码。

Docker 数据使用宿主机目录挂载。删除容器或执行 `down --volumes` 都不会清空这些目录。完全重新体验时，还需由操作者清理可丢弃的 Redis、RabbitMQ、MinIO 和 EMQX 状态，避免遗留会话、消息和文件。

独立的 TimescaleDB 使用自己的迁移，必须从空数据库开始。

## 相关页面

- [接入认证与 Session](/tutorials/authentication-and-session)
- [Sca4d Server 应用设计](/tutorials/assemble-application)
- [Sca4d 管理端设计](/tutorials/admin-feature)
- [Gateway 与回调](/tutorials/gateway-and-callbacks)
- [构建、部署与排障](/tutorials/build-deploy-troubleshoot)
- [运行配置](/reference/runtime/sca4d-server)
