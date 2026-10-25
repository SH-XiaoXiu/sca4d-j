# QuartzSchedulingProperties

源码：`modules/scheduling/sca4dj-scheduling-quartz/src/main/java/com/sca4d/scheduling/quartz/QuartzSchedulingProperties.java`

`QuartzSchedulingProperties` 配置当前实例的 Quartz 工作线程和 JDBC 集群签到间隔。前缀为 `sca4dj.scheduling.quartz`。

| 配置键 | 默认值 | 限制与作用 |
|---|---:|---|
| `sca4dj.scheduling.quartz.thread-count` | `4` | 大于零；Quartz 获取并提交 trigger 的工作线程数 |
| `sca4dj.scheduling.quartz.cluster-checkin-interval` | `PT15S` | 正数；实例写入 JDBC JobStore scheduler state 的间隔 |

```yaml
sca4dj:
  scheduling:
    quartz:
      thread-count: 4
      cluster-checkin-interval: PT15S
```

没有 `scheduler-name` 配置项。scheduler 名称由运行时命名空间派生，格式为：

```text
<应用隔离名称>:sca4dj:scheduling:quartz
```

具体应用必须配置 `spring.application.name`。同一应用的多个实例使用相同名称，并共享 Quartz 集群。同一 PostgreSQL 中的不同应用使用不同名称。

派生结果超过 `quartz.qrtz_*` 表 `sched_name` 的 120 字符限制时，自动配置在创建 Scheduler 前报告实际长度，并使启动失败。

模块固定使用主 DataSource、`PostgreSQLDelegate`、`LocalDataSourceJobStore`、集群模式、`useProperties=true` 和 `quartz.qrtz_` 表前缀。自动装配将 Scheduler 的 `autoStartup` 设为 `false`。模块不负责建表，具体应用的 Flyway 负责建表。`QuartzJobScheduler` 完成代码计划对账后启动 Scheduler。

`threadCount` 只影响 Quartz 触发提交，不是 Job 业务执行并发数。业务节点并发由 `JobProperties.workerCount` 控制。

配置无效时，应用启动失败，不会退回内存 JobStore 或非集群模式。对账和表的含义见 [Quartz 调度模块](/capabilities/jobs-scheduling/quartz)。

## 示例

以下配置适用于三个实例共享同一 PostgreSQL 的部署。三个实例使用同一 `spring.application.name`，因此共享同一个 Quartz 集群。

```yaml
spring:
  application:
    name: workspace-server
sca4dj:
  scheduling:
    quartz:
      thread-count: 4
      cluster-checkin-interval: PT15S
```

`thread-count` 决定每个实例同时提交 trigger 的线程数。业务节点的并发仍由 `sca4dj.job.worker-count` 决定，见 [Job 配置](/capabilities/jobs-scheduling/job-configuration)。

## 相关页面

- [Quartz 调度模块](/capabilities/jobs-scheduling/quartz)
- [Job 配置](/capabilities/jobs-scheduling/job-configuration)
- [Job 模块](/capabilities/jobs-scheduling/job)
