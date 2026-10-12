# 定义并触发后台 Job

Job 在后台以系统主体执行。人工请求可以提交 Job，但 Worker 不继承请求线程的 Session 或 Principal。先确定工作是否需要独立 Run、失败重试和结果查询；扫描业务持久状态通常用无记录 DirectJob，需要恢复节点的工作用持久化 DirectJob 或 PipelineJob。

## 登记周期扫描

已有扫描方法可以直接登记为无记录 Job，不必为每个扫描类复制一组元数据方法：

```java
@Bean
DirectJob<Void, Void> statusScanJob(StatusScan scan) {
    return DirectJob.unrecorded(
            new JobKey("APP/STATUS/SCAN"),
            1,
            TreeCode.ofColon("JOB:WORKER"),
            scan::execute);
}

@Bean
JobSchedule<Void, Void> statusScanSchedule(
        @Qualifier("statusScanJob") DirectJob<Void, Void> job) {
    return new JobSchedule<>(
            new ScheduleKey("APP/STATUS/SCAN"),
            1,
            job,
            null,
            new IntervalSchedule(Duration.ofSeconds(5), Duration.ofSeconds(5)),
            MisfirePolicy.FIRE_ONCE_NOW,
            OverlapPolicy.SKIP_IF_RUNNING);
}
```

当前实例必须启用声明的 `InstanceCapability`。该能力同时指出 Job 使用哪个 `SystemPrincipal`。默认 `JobTarget.any()` 表示合格实例共同承担一份工作；默认固定工作键使同一 Job 在系统身份内单活。扫描失败后，下一次触发重新读取业务状态。无记录执行不提供 Run 状态查询。

## 提交可恢复的工作

`DirectJob<I, O>` 声明输入输出类型、`JobKey`、定义版本、`RetryRule` 和一次 `execute(...)`。需要同一对象互斥、不同对象并行时覆盖 `workKey(input)`：

```java
@Override
public JobWorkKey workKey(Long reportId) {
    return JobWorkKey.of(TreeCode.ofColon("APP:REPORT"), reportId);
}
```

提交后检查 `JobSubmission`：

```java
JobSubmission submission = jobs.submit(generateReport, reportId);
if (submission.status() != JobSubmission.Status.SKIPPED) {
    Id runId = submission.runId().orElseThrow();
    JobRunStatus status = jobs.status(runId);
}
```

`CREATED` 创建了 Run；`EXISTING` 表示同一输入已有活动 Run；`SKIPPED` 表示同键工作正在处理其他输入，此时没有本次 Run ID。重试可能再次执行完整业务动作；文件绑定、设备命令和外部请求需要业务唯一约束或结果核对。

工作必须由特定实例执行时覆盖 `target(input)`，返回 `JobTarget.instance(instanceId, wait)`；需要触发时每个在线合格实例各执行一次时返回 `JobTarget.all(wait)` 并通过 `jobs.broadcast(job, input)` 取得广播 ID 和目标清单。等待期限只限制目标开始工作，运行中的份额另有租约。新上线实例不加入已经创建的广播。

多个业务步骤需要不同的恢复点时使用 PipelineJob。每条 Pipeline 是一个持久化节点，节点成功后其输出才交给后继；失败节点重试时不会重跑已经成功的前置节点。DataGraph 适合进程内只读数据装载，不保存可恢复节点。

提交、查询和等待的准确接口见 [JobExecutor](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/job/JobExecutor.html)；代码计划的登记接口见 [JobScheduler](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/scheduling/JobScheduler.html)。
