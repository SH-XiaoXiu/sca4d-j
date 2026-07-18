# 调度 API

## Schedule 服务

包：`com.sca4d.schedule`

| 类型 | 主要方法 | 默认实现与限制 |
| --- | --- | --- |
| `ScheduleService` | `page(...)`、`require(...)`、`create(...)`、`update(...)`、`delete(...)`、`pageExecutions(...)`、`requireExecution(...)`、`createManualExecution(...)`、`taskTypes()` | `DefaultScheduleService`；修改和删除使用 If-Match 版本 |
| `ScheduleExecutionService` | `run(...)` | `DefaultScheduleExecutionService`；同一 fireKey 只执行一次 |
| `SchedulePlanSynchronizer` | `synchronize(...)`、`remove(...)`、`reconcileAll()`、`startManualExecution(...)` | `ScheduleQuartzSynchronizer` |
| `ScheduleTask<P>` | `taskType()`、`payloadVersion()`、`payloadClass()`、`encode(...)`、`decode(...)`、`validate(...)`、`execute(...)`、`displayNameKey()` | 每个 taskType 只能注册一个实现，展示名称在 HTTP 边界按当前语言解析 |
| `ScheduleTaskRegistry` | `require(...)`、`tasks()`、`validateAndEncode(...)`、`payloadVersion(...)`、`run(...)` | taskType 必须匹配小写点分、横线或下划线格式；版本必须大于 0 |

## 状态与类型

| 类型 | 当前值 |
| --- | --- |
| `ScheduleType` | `ONCE`、`CRON` |
| `ScheduleStatus` | `ACTIVE`、`PAUSED`、`COMPLETED`、`FAILED`、`CANCELLED` |
| `ExecutionStatus` | 见[枚举与状态](/reference/enums-and-status.md#调度) |
| `TriggerSource` | 见[枚举与状态](/reference/enums-and-status.md#调度) |

`ONCE` 使用未来的 executeAt；`CRON` 使用 Quartz cronExpression 和 IANA timeZone。任务类型在 Schedule 创建后不可修改。

## 系统 Job

| 包 | 类型 | 主要方法 | 默认实现 |
| --- | --- | --- | --- |
| `com.sca4d.system` | `SystemScheduledJobService` | `jobs()`、`require(jobGroup, jobName)` | `DefaultSystemScheduledJobService` |
| `com.sca4d.schedule.quartz` | `SystemJobDefinition` | 系统 JobKey、Trigger 和展示信息 | 由清理与 Outbox Job 注册 |
| `com.sca4d.schedule.quartz` | `QuartzBootstrapCoordinator` | 启动时注册系统 Job 并核对业务 Schedule | Quartz 启用时运行 |
| `com.sca4d.schedule.quartz` | `SchedulerHealthIndicator` | Actuator 健康检查 | Scheduler 未启动或同步失败时为 DOWN |

## 错误码

`ScheduleErrorCode` 使用 3300 至 3310：

| 代码 | 枚举 | HTTP 分类 |
| ---: | --- | --- |
| 3300 | `SCHEDULE_NOT_FOUND` | 404 |
| 3301 | `SCHEDULE_INVALID` | 400 |
| 3302 | `SCHEDULE_TERMINAL` | 409 |
| 3303 | `SCHEDULE_TASK_TYPE_IMMUTABLE` | 409 |
| 3304 | `SCHEDULE_VERSION_CHANGED` | 412 |
| 3305 | `SCHEDULE_IF_MATCH_REQUIRED` | 428 |
| 3306 | `SCHEDULE_TASK_TYPE_UNKNOWN` | 400 |
| 3307 | `SCHEDULE_PAYLOAD_VERSION_UNKNOWN` | 409 |
| 3308 | `SCHEDULE_EXECUTION_NOT_FOUND` | 404 |
| 3309 | `SCHEDULE_EXECUTION_NOT_ALLOWED` | 409 |
| 3310 | `SCHEDULE_SYNCHRONIZATION_FAILED` | 500 |
