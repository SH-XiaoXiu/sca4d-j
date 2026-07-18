# 配置本地开发环境

## 目标

为主应用、网关和管理端创建本地配置，并确认提交内容中不包含凭据。

## 前置条件

- 已安装 JDK 21、Docker、Node.js 24 或更高版本和 pnpm。
- 仓库根目录存在 `.env.example`。
- PostgreSQL、Redis、RabbitMQ 和 MinIO 使用仓库中的 Compose 配置启动。

## 1. 创建环境变量文件

在仓库根目录创建仅供本机使用的 `.env`：

```bash
cp .env.example .env
```

`.env` 已被 Git 忽略。不要把密码、Token、访问密钥或生产地址写入 `.env.example` 和 YAML 默认值。

## 2. 检查本地端口

Compose 默认使用以下端口。端口被占用时，在 `.env` 中修改对应变量，不要直接修改共享配置。

| 服务 | 默认端口 |
| --- | --- |
| PostgreSQL | `5432` |
| Redis | `6379` |
| RabbitMQ AMQP | `5672` |
| RabbitMQ 管理端 | `15672` |
| MinIO API | `9000` |
| MinIO Console | `9001` |
| 主应用 | `8080` |
| Gateway | `8088` |

完整变量名和默认值见[配置参考](/reference/configuration)。

## 3. 启动并检查依赖

```bash
docker compose up -d
docker compose ps
```

`docker compose ps` 中的依赖应处于运行或健康状态。连接失败时，先核对 `.env` 中的主机、端口、用户名和密码，再查看[本地依赖](/operations/local-dependencies)。

## 4. 启动主应用

```bash
./gradlew :sca4d-server:bootRun
```

本地启动使用 `local` Profile。应用会在接受请求前执行数据库迁移、配置校验和启动检查。任一必需依赖不可用时，启动失败并打印具体检查项。

## 5. 配置管理端代理

进入管理端工作区并安装依赖：

```bash
cd sca4d-admin
pnpm install
pnpm dev
```

管理端开发服务器应把 API 请求转发到主应用。代理地址、OpenAPI 来源或端口发生变化时，只修改本地环境值，并通过登录和 `GET /me` 验证真实连接。

## 运行结果

- `docker compose ps` 显示本地依赖可用。
- 主应用健康端点返回成功。
- 管理端能够连接主应用并显示登录页。
- `git status --short` 不包含 `.env`。

配置加载顺序、生产环境要求和全部变量见[配置组织与覆盖](/operations/configuration)和[配置参考](/reference/configuration)。
