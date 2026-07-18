# 本地依赖

## Docker Compose

```bash
docker compose config
docker compose up -d
docker compose ps
docker compose logs postgresql redis rabbitmq minio
```

默认只绑定 `127.0.0.1`，数据写入根目录 `docker-data/`。不要把本地数据目录提交到 Git。

## PostgreSQL

Compose 使用 PostgreSQL 17 Alpine，默认数据库和用户为 `sca4dj`。服务时区与连接时区应明确；主应用通过 Flyway 管理 schema，不手工建表代替迁移。

## Redis

Compose 使用 Redis 7.2，保存 Sa-Token 共享状态、缓存、锁、限流和幂等记录。本地无密码时 `SCA4DJ_REDIS_PASSWORD` 留空；生产必须按平台安全策略配置。

## RabbitMQ

Compose 使用 RabbitMQ 3.13 management，并启用 STOMP 插件。端口 `5672` 用于 AMQP，`61613` 用于 STOMP relay，`15672` 用于本地管理页面。应用启用 publisher confirm 和 mandatory return。

## MinIO

MinIO API 默认 `9000`，Console 默认 `9001`。服务端 endpoint 与浏览器 public endpoint 可以不同；反代时公开地址必须能让浏览器直接访问预签名对象。

## 数据目录与重建

开发数据可重建前，先确认没有需要保留的业务数据。Flyway 历史与分支不一致时优先新建干净 schema/数据目录验证当前源码，不在共享数据库随意删除 history 或 repair。

