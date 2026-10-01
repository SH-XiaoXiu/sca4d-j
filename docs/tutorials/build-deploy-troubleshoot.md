# 构建、部署与排障

部署包含 Server JAR、Gateway JAR 和管理端静态文件，分别配置并启动。先备份数据库、检查迁移与单实例 readiness，再通过 Gateway 验证登录和本次发布功能；请求失败时用响应头 `X-Call-Id` 定位日志。

## 构建应用

在仓库根目录运行：

```bash
GRADLE_USER_HOME=/tmp/sca4dj-gradle \
GRADLE_OPTS='-Dorg.gradle.jvmargs=-Xmx1g -XX:MaxMetaspaceSize=384m' \
nice -n 10 ./gradlew --no-daemon --max-workers=1 \
  :apps:sca4d-server:bootJar :gateway:sca4d-gateway:bootJar
```

JAR 位于对应项目的 `build/libs/`。Sca4d Server 文件名包含构建版本。管理端在独立工作区构建：

```bash
cd frontends/sca4d-admin
pnpm install --frozen-lockfile
NODE_OPTIONS='--max-old-space-size=1024' nice -n 10 pnpm build
```

静态文件交给 Web 服务器托管，Gateway 只转发 API 和长连接。部署配置以[运行配置](/reference/runtime/sca4d-server)为准，数据库、Redis、RabbitMQ、存储和认证密钥由运行环境注入，不写入前端产物。

## 启动与发布检查

使用部署平台向每个进程注入所需配置，再以 `java -jar <本次构建的 JAR 路径>` 启动。数据库必须已经备份并具备执行本次 Flyway 迁移的条件。迁移失败时先检查失败 SQL 和数据库状态，不删除迁移历史或用重建 V1 跳过错误。

同一应用的多个 Server 使用相同的 `spring.application.name` 和共享基础设施，每个实例使用不同的实例 ID。Gateway Secret 保持一致，Server 注册的 Origin 从 Gateway 可达。多 Gateway 的注册限制见[Gateway](/tutorials/gateway-and-callbacks#失败与集群限制)。

先检查单个实例的 readiness，再确认 Gateway 已选择该实例，最后通过公开 `/api` 入口执行登录和本次发布涉及的业务操作。使用强 ETag 的接口还需检查反向代理没有将标签改成弱 ETag，配置示例见[HTTP 参考](/reference/http/common)。

## 从失败响应定位

| 现象 | 先检查什么 |
|---|---|
| Gateway 返回 503 | Server 租约、readiness、目标回调编码是否已注册 |
| Gateway 返回 502 | 已选择实例的地址、端口和网络连通性 |
| 请求返回 401 | Token 是否过期、接口是否强制在线 Session Token |
| 请求返回 403 | 当前 Principal、Operation 和对象权限 |
| 修改返回 412 或 428 | 是否读取最新强 ETag、是否提交 `If-Match` |
| 返回 429 | `Retry-After` 和限流维度；等待后再请求 |
| 上传失败 | 签发的 method、URL、headers、CORS、实际文件类型与大小 |
| 后台任务没有结束 | run 状态、节点尝试次数、租约和最终错误 |

记录响应头 `X-Call-Id`，用它查询 Server 日志。先区分应用拒绝、依赖不可用和请求未到达，保留发生时间、接口与安全错误码。日志中不复制 Token、密码、签名 URL 或完整请求体。

## 部署集中日志

完成以下操作后，中心主机运行 Sca4d Gateway、Grafana 和单体 Loki，每台 Java 主机运行一个 Alloy。Java 进程继续写本地 ECS JSON 文件，Alloy 通过 Gateway 的专用写入路由集中发送。

### 准备配置

中心主机和每台 Java 主机都需要 Docker 与 Docker Compose。先创建本机配置；已有文件不会被覆盖：

```bash
if [ ! -e .env/logging.env ]; then
  cp .env/logging.env.example .env/logging.env
fi
```

替换 Grafana 管理员密码和 Loki 写入密码。中心 Gateway 与所有 Alloy 必须使用相同的写入账号密码；该密码不能等于 `SCA4DJ_GATEWAY_SECRET`。每台 Java 主机把 `SCA4DJ_LOG_HOST` 改成唯一且稳定的地域或主机名，并设置正确的 `SCA4DJ_ENVIRONMENT`。

各地 Alloy 把 `SCA4DJ_LOKI_PUSH_URL` 设置为中心 Gateway 的 `/internal/logging/push` 地址。跨公网时必须使用 HTTPS，例如：

```properties
SCA4DJ_LOKI_PUSH_URL=https://gateway.example.com/internal/logging/push
```

### 启动中心端

先启动 Loki 与 Grafana：

```bash
docker compose \
  --env-file .env/logging.env \
  -f docker-compose.logging.yml \
  up -d

docker compose \
  --env-file .env/logging.env \
  -f docker-compose.logging.yml \
  ps
```

本地运行的 Gateway 通过 `application-local.yml` 自动读取 `.env/application.env` 和 `.env/logging.env`。部署 JAR 时只向 Gateway 进程注入下面这些变量，不要让 Gateway 读取 Grafana 和对象存储密码：

```properties
SCA4DJ_GATEWAY_LOGGING_ENABLED=true
SCA4DJ_GATEWAY_LOKI_ORIGIN=http://127.0.0.1:3100
SCA4DJ_GATEWAY_LOGGING_PUSH_PATH=/internal/logging/push
SCA4DJ_GATEWAY_LOKI_PATH=/loki/api/v1/push
SCA4DJ_LOKI_PUSH_USERNAME=alloy
SCA4DJ_LOKI_PUSH_PASSWORD=replace-me
```

默认 `sca4d-gateway` BootJar 通过 `runtimeOnly` 包含 `sca4dj-gateway-loki`。自定义 Gateway 组装工程必须显式添加该 addon 依赖；未添加依赖时，设置 `SCA4DJ_GATEWAY_LOGGING_ENABLED=true` 不会创建日志写入路由。

启动 Gateway 后，未带凭据的写入请求应返回 401，而不是进入 Loki：

```bash
curl -i -X POST http://127.0.0.1:8088/internal/logging/push
```

打开 `SCA4DJ_GRAFANA_BIND` 对应的地址，使用 Grafana 管理员账号登录。数据源页面中的「Loki」应显示连接成功，并可在 Explore 中选择该数据源。

使用 S3 或 MinIO 时，先创建独立 Bucket 和最小权限凭据，再设置：

```properties
SCA4DJ_LOKI_STORAGE_MODE=s3
SCA4DJ_LOKI_S3_ENDPOINT=https://minio.example.com
SCA4DJ_LOKI_S3_REGION=us-east-1
SCA4DJ_LOKI_S3_BUCKET=loki-logs
SCA4DJ_LOKI_S3_ACCESS_KEY=replace-me
SCA4DJ_LOKI_S3_SECRET_KEY=replace-me
SCA4DJ_LOKI_S3_INSECURE=false
```

这里的 MinIO/S3 地址只供 Loki 保存数据。Alloy 仍然连接 Gateway，不连接 MinIO。切换存储后必须重启 Loki；文件系统和 S3 中的日志不会自动迁移或合并。

### 配置应用日志目录

Java 进程按服务和实例写入 `SCA4DJ_LOG_ROOT`。不要在这些进程共用的 `.env/logging.env` 中设置 `SCA4DJ_INSTANCE_ID`；同一主机并行启动多个实例时，为每个进程单独指定不同实例 ID：

```bash
SCA4DJ_INSTANCE_ID=server-1 ./gradlew :apps:sca4d-server:bootRun
```

容器中的 Java 应用使用同一目录规则。把宿主机根目录挂载到容器，并让容器内外使用一致的相对结构：

```yaml
environment:
  SCA4DJ_LOG_ROOT: /var/log/applications
  SCA4DJ_INSTANCE_ID: server-1
volumes:
  - ${SCA4DJ_LOG_ROOT:-./logs}:/var/log/applications
```

检查活动文件是一行一个 JSON 对象，且路径包含服务和实例：

```bash
find "${SCA4DJ_LOG_ROOT:-./logs}" -name application.json -type f -print
```

### 启动每台主机的 Alloy

在每台 Java 主机执行：

```bash
docker compose \
  --env-file .env/logging.env \
  -f docker-compose.logging-agent.yml \
  up -d

docker compose \
  --env-file .env/logging.env \
  -f docker-compose.logging-agent.yml \
  logs --tail=100 logging-alloy
```

Alloy 管理端点默认只监听 `127.0.0.1:12345`。它只读取 `<service>/<instance>/application.json`，不读取压缩归档、Docker Socket 或基础设施容器日志。

### 检查检索

让任一 Java 实例产生一条可识别的日志，然后在 Grafana Explore 中先按地域主机、服务和实例筛选，再解析 JSON 字段：

```logql
{environment="prod", host="shanghai-1", service="example-server", instance="server-1"} | json
```

需要按调用标识定位 Server 日志时继续增加过滤条件：

```logql
{service="example-server"} | json | callId="logging-check-1"
```

### 失败入口

- Alloy 出现 401 时，核对 Gateway 与该主机的写入用户名、密码和精确路径；不要改用 `SCA4DJ_GATEWAY_SECRET`。
- Alloy 出现 404 时，确认中心 Gateway 包含 `sca4dj-gateway-loki`、已设置 `SCA4DJ_GATEWAY_LOGGING_ENABLED=true`，并且 `SCA4DJ_LOKI_PUSH_URL` 与 `SCA4DJ_GATEWAY_LOGGING_PUSH_PATH` 一致。
- Alloy 出现 502 时，确认中心 Gateway 能访问 `SCA4DJ_GATEWAY_LOKI_ORIGIN`，并检查 Loki 是否就绪。
- Java 文件不存在时，核对进程实际读取的 `SCA4DJ_LOG_ROOT` 和 `SCA4DJ_INSTANCE_ID`，再检查目录写权限。
- Loki 因 S3 启动失败时，核对 Bucket 已存在、endpoint、region 和 path-style 是否匹配，以及凭据是否能读写并删除 Loki 自己的对象。

准确变量、认证和保留行为见[集中日志运行规格](/reference/runtime/sca4d-server)。
