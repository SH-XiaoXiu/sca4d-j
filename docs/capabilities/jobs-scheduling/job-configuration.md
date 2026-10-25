# JobProperties

源码：`modules/job/sca4dj-job/src/main/java/com/sca4d/job/config/JobProperties.java`

`JobProperties` 配置当前应用每个实例的 JobWorker 容量、租约和成功历史清理。前缀为 `sca4dj.job`。

| 配置键 | 默认值 | 限制与作用 |
|---|---:|---|
| `sca4dj.job.worker-count` | `4` | 大于零；每个实例同时执行业务节点的线程上限 |
| `sca4dj.job.claim-batch-size` | `10` | 大于零；每轮最多领取数，实际还受空闲 worker 数限制 |
| `sca4dj.job.poll-interval` | `PT0.5S` | 正数；无节点和领取完成后的固定轮询间隔 |
| `sca4dj.job.lease-duration` | `PT1M` | 正数；worker 失联后其他实例可接管的等待时间 |
| `sca4dj.job.lease-renew-interval` | `PT20S` | 正数且严格小于 leaseDuration |
| `sca4dj.job.succeeded-retention` | `P30D` | 正数；从 `completed_at` 起保留成功根运行 |
| `sca4dj.job.history-cleanup-interval` | `PT1H` | 正数；相邻两次成功历史清理的间隔 |
| `sca4dj.job.history-cleanup-batch-size` | `500` | 大于零；单次最多删除的成功根运行数 |

```yaml
sca4dj:
  job:
    worker-count: 4
    claim-batch-size: 10
    poll-interval: PT0.5S
    lease-duration: PT1M
    lease-renew-interval: PT20S
    succeeded-retention: P30D
    history-cleanup-interval: PT1H
    history-cleanup-batch-size: 500
```

`workerCount` 是单个实例的容量。三个相同应用隔离名称（`AppName`）的实例各配置 4 时，理论上最多同时执行 12 个节点。`claimBatchSize` 大于 `workerCount` 时，也不会让一个实例超出线程池容量。

`leaseDuration` 应覆盖正常的短暂停顿和数据库延迟。`leaseRenewInterval` 应留出多次续租机会。

租约过短会使仍在执行的节点被其他实例重复领取。租约过长会延迟失联恢复。

无论怎样配置，运行方式仍是至少一次。不能依靠租约参数获得仅一次外部操作。

历史清理只选择当前应用隔离名称中、`completed_at` 超过 `succeededRetention` 的 `SUCCEEDED` 根运行。删除根运行时，`JobRepository` 在同一事务中依次删除 attempt、依赖、节点和根运行。`FAILED`、`QUEUED` 和 `RUNNING` 不按该窗口自动删除。删除后的 Run 标识按不存在处理。

配置在 JobWorker Bean 创建前统一校验。数量、间隔、保留时间无效，或续租间隔不小于租约时长时，应用启动失败。应用不会退回进程本地执行或无租约模式。运行和表状态见 [Job 模块](/capabilities/jobs-scheduling/job)。

## 示例

以下配置适用于三个实例共享同一数据库的部署。每个实例最多同时执行 4 个节点，租约 1 分钟，续租间隔 20 秒。

```yaml
sca4dj:
  job:
    worker-count: 4
    lease-duration: PT1M
    lease-renew-interval: PT20S
```

上例中，续租间隔 20 秒小于租约 1 分钟，满足启动校验。若把 `lease-renew-interval` 设为 `PT1M`，等于租约时长，应用启动失败。

## 相关页面

- [Job 模块](/capabilities/jobs-scheduling/job)
- [Pipeline、Job 与定时调度](/capabilities/jobs-scheduling/pipeline-job-scheduling)
- [Quartz 配置](/capabilities/jobs-scheduling/quartz-configuration)
