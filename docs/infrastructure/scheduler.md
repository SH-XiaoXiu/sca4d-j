# Quartz 集群调度

主应用使用 Quartz JDBC JobStore 执行定时任务。所有实例连接同一 PostgreSQL，Quartz 负责触发器存储、节点签到、故障恢复和单次触发竞争。

## 数据表

`QRTZ_` 系列表保存 Quartz 的持久化状态。Spring Boot 不自动创建或更新这些表。

业务调度使用三张业务表：

- `schedule_definition`：一次性计划和 Cron 计划的定义；
- `schedule_execution`：每次计划触发、人工触发或恢复执行的结果；
- `schedule_job_runtime`：系统 Job 最近一次运行时间、结果、耗时和实例 ID。

## 启动过程

Quartz 的 `auto-startup` 为 `false`。应用收到 `ApplicationReadyEvent` 后执行以下操作：

1. 根据 scheduler name 计算 PostgreSQL advisory lock key。
2. 在配置的超时时间内取得启动锁。
3. 对比数据库中的系统 Job、业务 Schedule 与当前代码定义。
4. 注册系统 Job 执行监听器。
5. 对账成功后启动 Quartz；失败时保持未启动，并把 health 设为 `DOWN`。

启动锁只保护对账过程。任务执行竞争由 Quartz JDBC JobStore 和 `schedule_execution.fire_key` 唯一键处理。

可靠性由三层独立约束组成：Quartz 集群保证同一 Trigger fire 的单节点获取，`@DisallowConcurrentExecution` 防止同一 JobKey 的不同 fire 重叠，业务唯一 fireKey、任务事务和 recovery 复用原 fireKey 保证业务幂等。任何一层都不能单独宣称 exactly-once。

## 系统 Job

`SystemJobQuartzSynchronizer` 始终注册 Subject 和 Tenant 清理 Job，并按消息、Outbox 和文件能力开关注册对应 Job：

| Job | 周期配置 | 执行内容 |
| --- | --- | --- |
| `subject-cleanup` | `SCA4DJ_IAM_CLEANUP_INTERVAL` | 清理已进入删除状态的 Subject 关系 |
| `tenant-cleanup` | `SCA4DJ_IAM_CLEANUP_INTERVAL` | 清理已进入删除状态的 Tenant 运行时关系 |
| `message-outbox-dispatch` | `SCA4DJ_MESSAGING_OUTBOX_POLL_INTERVAL` | 消息和 Outbox 均启用时投递到期记录 |
| `file-cleanup` | `SCA4DJ_FILE_CLEANUP_INTERVAL` | 文件能力启用时清理过期文件和等待删除的对象 |

代码中的修订号、Job 类型或触发周期变化时，对账器更新 Quartz 定义。能力关闭后，对账器删除 `system` 分组中不再启用的旧 Job。同一修订号对应的定义指纹不一致时启动失败。

系统 Job 只能通过 `GET /mg/system/scheduled-jobs` 及其详情接口观测，不提供写操作。Gateway 不加载 Quartz；它的 upstream 配置轮询与健康检查是节点本地任务。

## 业务 Schedule

`ScheduleTask<P>` 定义 task type、Payload 版本、编解码、校验和执行方法。`ScheduleTaskRegistry` 拒绝重复 task type。当前已注册 `NotificationScheduleTask`。

一次性计划使用 `ScheduleType.ONCE`，Cron 计划使用 `ScheduleType.CRON`。执行记录的 `fire_key` 在同一 Schedule 内唯一，重复恢复或重复触发不能取得第二次执行权。

Schedule 是唯一可管理的业务调度资源。`/mg/platform/schedules` 使用资源式 GET、POST、PUT、DELETE；创建和人工 execution 使用 `Idempotency-Key`，完整 PUT 与 DELETE 使用强 ETag/`If-Match`。ACTIVE 与 PAUSED 通过完整 PUT 表达，不提供 pause、resume 或 run 动作路由。

## 停机与健康状态

应用关闭时先让 Scheduler 进入 standby，再等待本实例正在执行的 Job。等待时间超过 `SCA4DJ_SCHEDULER_SHUTDOWN_WAIT` 后停止等待并关闭 Scheduler。

`schedulerHealthIndicator` 检查 Scheduler 已启动、未关闭、使用持久化 JobStore，并且最近一次对账成功。任一条件不满足时 readiness 为 `DOWN`。

全部参数见[配置参考](/reference/configuration.md#quartz-集群调度)。
