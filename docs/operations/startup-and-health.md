# 启动、健康与观测

## Profile

应用解析唯一 `BootEnvironment`。staging/prod 被视为生产环境；未知或多个环境拒绝启动。完整表见[环境划分](./environments.md)。

## BootCheck

每个 `BootCheck` 声明名称、启用、严重等级和超时，在独立虚拟线程执行：

| 级别 | 启动行为 | 健康行为 |
| --- | --- | --- |
| `REQUIRED` | staging/prod 失败阻止启动 | DOWN |
| `DEGRADED` | 启动并警告 | DOWN |
| `OPTIONAL` | 启动并记录 | 不使整体 DOWN |

当前 MinIO 提供启动检查；生产环境还拒绝默认 MinIO 凭据。检查消息不得包含连接秘密。

## 健康端点

```text
GET /actuator/health
GET /actuator/health/liveness
GET /actuator/health/readiness
```

详情默认不公开。readiness 表示能否接流量，liveness 表示进程是否需重启。消息能力关闭时 Rabbit 健康检查同步关闭。

Quartz 还提供 `schedulerHealthIndicator`。Scheduler 未启动、已关闭、没有使用 JDBC JobStore 或启动对账失败时，readiness 为 `DOWN`。

## 启动摘要

容器就绪后 `BootUtils` 输出环境、本地服务地址、OpenAPI 状态和最近 BootCheck 摘要。它用于确认实际启动配置，不是生产对外地址清单。

## 访问日志与应用日志

访问日志记录方法、URI、状态、耗时、traceId、Subject、Account、Principal、客户端地址和查询参数名，不记录查询值和 body。应用日志记录内部诊断，操作日志记录声明审计的业务动作。

## 指标与告警

至少监控 readiness、5xx/慢请求、连接池、Outbox 待投递/失败、RabbitMQ、Redis、数据库、文件清理失败和 JVM 资源。阈值以业务 SLO 和容量测试确定，不能只监控进程存活。
