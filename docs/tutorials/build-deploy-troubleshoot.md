# 构建、部署与排障

场景：把一个包含工作区（Workspace）、成员（Member）和文档（Document）功能的版本发布到服务器，发布后某个请求返回错误，需要用调用标识定位原因。

发布包含三个产物：Server JAR、Gateway JAR 和管理端静态文件。三者分别配置与部署。发布后，先检查单实例 readiness，再通过 Gateway 验证登录和本次发布涉及的功能。请求失败时，用响应头 `X-Call-Id` 定位日志。

前置条件：数据库已经备份，并具备执行本次 Flyway 迁移的条件。

## 实现步骤

### 1. 运行发布前检查

在仓库根目录运行。检查命令默认限制 CPU 与内存：

```bash
./gradlew --max-workers=1 \
  -Dorg.gradle.jvmargs="-Xmx1g -XX:MaxMetaspaceSize=384m" \
  :apps:<应用名>-server:test :apps:<应用名>-server:integrationTest
```

集成测试需要 Docker 和 Testcontainers。

根工程还提供聚合任务：`test`、`integrationTest`、`formatCheck`、`audit` 和 `documentationSite`。聚合任务覆盖全部已映射的模块，只在影响整个仓库的发布前使用。

管理端检查在 `frontends/<应用名>-admin/` 执行：

```bash
pnpm install --frozen-lockfile
NODE_OPTIONS='--max-old-space-size=1024' nice -n 10 pnpm typecheck
NODE_OPTIONS='--max-old-space-size=1024' nice -n 10 pnpm test
```

### 2. 构建 JAR

```bash
GRADLE_OPTS='-Dorg.gradle.jvmargs=-Xmx1g -XX:MaxMetaspaceSize=384m' \
nice -n 10 ./gradlew --max-workers=1 \
  :apps:<应用名>-server:bootJar :gateway:sca4d-gateway:bootJar
```

JAR 位于对应模块的 `build/libs/`，文件名为 `<模块名>-<构建版本>.jar`。

构建版本的规则：

| 命令参数 | 版本 |
|---|---|
| 无 | `<提交短哈希>-<yyyyMMdd-HHmm>` |
| `-Prelease` | `<最近的 Git tag>-<提交短哈希>`，没有 tag 时使用 `untagged` |
| `-Prelease -Prelease.version=<版本>` | `<版本>-<提交短哈希>` |

### 3. 构建管理端

```bash
cd frontends/<应用名>-admin
pnpm install --frozen-lockfile
NODE_OPTIONS='--max-old-space-size=1024' nice -n 10 pnpm build
```

产物位于 `apps/<应用名>-admin/dist`。把它交给 Web 服务器托管。Gateway 只转发 API 和长连接，不托管静态文件。

数据库、Redis、RabbitMQ、存储和认证密钥由运行环境注入，不写入前端产物。

### 4. 启动并检查

使用部署平台向每个进程注入配置，再启动：

```bash
java -jar <本次构建的 JAR 路径>
```

配置项见[运行配置](/reference/runtime/sca4d-server)。

多实例的条件：

- 同一应用的 Server 使用相同的 `spring.application.name` 和共享基础设施。
- 每个实例使用不同的实例 ID。
- 所有 Server 与 Gateway 使用相同的 Gateway Secret。
- Server 注册的 Origin 必须能从 Gateway 访问。

多 Gateway 的注册限制见 [Gateway 与回调](/tutorials/gateway-and-callbacks#失败与集群限制)。

使用强 ETag 的接口还需检查反向代理没有把 `ETag: "3"` 改成弱 ETag `W/"3"`。配置示例见 [HTTP 通用约定](/reference/http/common)。

检查顺序：

1. 单个 Server 实例的 readiness：

```bash
curl --silent http://<Server 地址>/actuator/health/readiness
```

2. Gateway 自身状态：

```bash
curl --silent http://<Gateway 地址>/actuator/health
```

3. 通过公开入口登录，并执行本次发布涉及的操作：

```bash
curl --fail-with-body --silent --show-error \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'X-Call-Id: release-check-1' \
  -i http://<Gateway 地址>/api/sessions/current
```

### 5. 从失败响应定位

记录响应头 `X-Call-Id`。请求没有提供合法值时，Server 生成 UUID；请求提供的值符合 `[A-Za-z0-9._-]{1,128}` 时，Server 沿用该值。

先区分三种情况：应用拒绝、依赖不可用、请求未到达。记录发生时间、接口和安全错误码。日志中不复制 Token、密码、签名 URL 或完整请求体。

然后用调用标识查询 Server 日志。`callId` 写入日志上下文（MDC）的 `callId` 字段，ECS JSON 日志包含该字段。文本日志是否输出它，取决于日志格式配置。
同一次调用从入口到日志使用同一个 `callId`，因此一个响应头可以关联 Gateway、Server 和集中日志。

启用集中日志后，使用下文的 LogQL 查询。

### 6. 部署集中日志

中心主机运行 Gateway、Grafana 和单体 Loki。每台 Java 主机运行一个 Alloy。Java 进程写本地 ECS JSON 文件，Alloy 通过 Gateway 的专用写入路由发送。

中心主机和每台 Java 主机都需要 Docker 与 Docker Compose。

先创建配置，已有文件不会被覆盖：

```bash
if [ ! -e .env/logging.env ]; then
  cp .env/logging.env.example .env/logging.env
fi
```

在 `.env/logging.env` 中完成以下修改：

- 替换 Loki 写入密码 `SCA4DJ_LOKI_PUSH_PASSWORD`。中心 Gateway 与所有 Alloy 使用相同的写入账号密码，且该密码不能等于 `SCA4DJ_GATEWAY_SECRET`。
- 中心主机补充 `SCA4DJ_GRAFANA_ADMIN_PASSWORD`。Compose 要求设置该变量，示例文件不包含它。
- 每台 Java 主机补充唯一且稳定的 `SCA4DJ_LOG_HOST`，并设置 `SCA4DJ_ENVIRONMENT`。
- Alloy 所在主机把 `SCA4DJ_LOKI_PUSH_URL` 设置为中心 Gateway 的写入地址。跨公网必须使用 HTTPS：

```properties
SCA4DJ_LOKI_PUSH_URL=https://gateway.example.com/internal/logging/push
```

启动中心端的 Loki 与 Grafana：

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

Compose 挂载 `docker/logging/loki.yml` 和 `docker/logging/grafana/provisioning`。Alloy 的 Compose 挂载 `docker/logging/alloy/config.alloy`。这些文件缺少时，容器启动失败。

部署 Gateway JAR 时，只向 Gateway 进程注入以下变量。不要让 Gateway 读取 Grafana 和对象存储密码：

```properties
SCA4DJ_GATEWAY_LOGGING_ENABLED=true
SCA4DJ_GATEWAY_LOKI_ORIGIN=http://127.0.0.1:3100
SCA4DJ_GATEWAY_LOGGING_PUSH_PATH=/internal/logging/push
SCA4DJ_GATEWAY_LOKI_PATH=/loki/api/v1/push
SCA4DJ_LOKI_PUSH_USERNAME=alloy
SCA4DJ_LOKI_PUSH_PASSWORD=replace-me
```

默认的 `sca4d-gateway` BootJar 通过 `runtimeOnly` 包含 `sca4dj-gateway-loki`。自定义 Gateway 组装工程必须显式添加该依赖。缺少依赖时，`SCA4DJ_GATEWAY_LOGGING_ENABLED=true` 不创建写入路由。

使用 S3 或 MinIO 保存 Loki 数据时，先创建独立 Bucket 和最小权限凭据，再设置：

```properties
SCA4DJ_LOKI_STORAGE_MODE=s3
SCA4DJ_LOKI_S3_ENDPOINT=https://minio.example.com
SCA4DJ_LOKI_S3_REGION=us-east-1
SCA4DJ_LOKI_S3_BUCKET=loki-logs
SCA4DJ_LOKI_S3_ACCESS_KEY=replace-me
SCA4DJ_LOKI_S3_SECRET_KEY=replace-me
SCA4DJ_LOKI_S3_INSECURE=false
```

这里的地址只供 Loki 保存数据。Alloy 仍然连接 Gateway，不连接 MinIO。切换存储后必须重启 Loki。文件系统和 S3 中的日志不会自动迁移或合并。

配置应用日志目录。采用 ECS JSON 日志配置的进程，在 `SCA4DJ_LOG_ROOT/<服务名>/<实例 ID>/` 下写 `application.json`。不要在多个进程共用的 `.env/logging.env` 中设置 `SCA4DJ_INSTANCE_ID`。同一主机并行启动多个实例时，为每个进程单独指定：

```bash
SCA4DJ_INSTANCE_ID=server-1 ./gradlew :apps:<应用名>-server:bootRun
```

容器中的 Java 应用使用同一目录规则：

```yaml
environment:
  SCA4DJ_LOG_ROOT: /var/log/applications
  SCA4DJ_INSTANCE_ID: server-1
volumes:
  - ${SCA4DJ_LOG_ROOT:-./logs}:/var/log/applications
```

检查活动文件：

```bash
find "${SCA4DJ_LOG_ROOT:-./logs}" -name application.json -type f -print
```

在每台 Java 主机启动 Alloy：

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

Alloy 管理端点默认只监听 `127.0.0.1:12345`。Alloy 只读取 `<service>/<instance>/application.json`，不读取压缩归档、Docker Socket 或基础设施容器日志。

## 验证

准确变量、认证和保留行为见[集中日志运行规格](/reference/runtime/sca4d-server#集中日志运行规格)。

Gateway 启动后，未带凭据的写入请求应返回 401，而不是进入 Loki：

```bash
curl -i -X POST http://127.0.0.1:8088/internal/logging/push
```

打开 `SCA4DJ_GRAFANA_BIND` 对应的地址，默认 `127.0.0.1:3000`，用 Grafana 管理员账号登录。数据源页面中的「Loki」应显示连接成功，Explore 中可以选择该数据源。

让任一 Java 实例产生一条可识别的日志，再在 Grafana Explore 中检索。先按环境、主机、服务和实例筛选，再解析 JSON 字段：

```logql
{environment="prod", host="shanghai-1", service="example-server", instance="server-1"} | json
```

按调用标识定位：

```logql
{service="example-server"} | json | callId="release-check-1"
```

## 失败结果与限制

| 现象 | 先检查什么 |
|---|---|
| Gateway 返回 503 | 没有租约有效、readiness 成功且支持目标回调编码的 Server 实例。检查 Server 租约、readiness 和回调编码是否已注册。 |
| Gateway 返回 502 | 已选中实例，但 TCP 连接失败。检查实例的地址、端口和网络连通性。 |
| 请求返回 401 | Token 是否过期，接口是否要求在线 Session Token。 |
| 请求返回 403 | 当前 Principal、Operation 和对象权限。 |
| 修改返回 428，错误码 `1105` | 请求没有提交 `If-Match`。 |
| 修改返回 412，错误码 `1106` | `If-Match` 不是单个强 ETag，或资源版本已经变化。重新读取资源，再提交。 |
| 返回 429，错误码 `1005` | 读取 `Retry-After`，等待该秒数后再请求。 |
| 上传失败 | 签发的 method、URL、headers、CORS，以及实际文件类型与大小。 |
| 后台任务没有结束 | run 状态、节点尝试次数、租约和最终错误。 |
| 迁移失败 | 失败的 SQL 和数据库状态。不要删除迁移历史，也不要用重建 V1 跳过错误。 |
| 强 ETag 接口持续返回 412 | 反向代理把强 ETag 改成了弱 ETag。关闭该 location 的 gzip。 |

集中日志的失败入口：

- Alloy 出现 401 时，核对 Gateway 与该主机的写入用户名、密码和精确路径。不要改用 `SCA4DJ_GATEWAY_SECRET`。
- Alloy 出现 404 时，确认中心 Gateway 包含 `sca4dj-gateway-loki`，已设置 `SCA4DJ_GATEWAY_LOGGING_ENABLED=true`，并且 `SCA4DJ_LOKI_PUSH_URL` 与 `SCA4DJ_GATEWAY_LOGGING_PUSH_PATH` 一致。
- Alloy 出现 502 时，确认中心 Gateway 能访问 `SCA4DJ_GATEWAY_LOKI_ORIGIN`，并检查 Loki 是否就绪。
- Java 日志文件不存在时，核对进程实际读取的 `SCA4DJ_LOG_ROOT` 和 `SCA4DJ_INSTANCE_ID`，再检查目录写权限。
- Loki 因 S3 启动失败时，核对 Bucket 已存在，endpoint、region 和 path-style 匹配，凭据能读写并删除 Loki 自己的对象。

## 相关页面

- [第一次运行](/tutorials/local-start)
- [Gateway 与回调](/tutorials/gateway-and-callbacks)
- [测试](/tutorials/testing)
- [常见问题](/tutorials/faq)
- [运行配置](/reference/runtime/sca4d-server)
- [HTTP 通用约定](/reference/http/common)
- [构建插件](/reference/build-plugins)
