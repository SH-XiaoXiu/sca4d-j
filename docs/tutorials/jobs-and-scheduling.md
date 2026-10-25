# 定义并触发后台 Job

本页以 Workspace 的两项后台工作为例。
第一项是每隔数秒扫描已过期的 Document 分享，并撤销访问权限。
第二项是 Member 请求导出 Workspace 内容，生成 Attachment 后通知请求人。

扫描失败后，下一次触发会重新读取业务状态，不需要保存运行记录。
导出需要独立 Run、失败重试和结果查询。
读完本页后，可以完成以下工作：

- 把已有扫描方法登记为周期执行的无记录 Job；
- 定义可恢复的 `DirectJob`，提交后查询 Run 状态；
- 接入自己的 Job 与计划，并确认启动校验通过。

Job 在后台以系统主体执行。
人工请求可以提交 Job，但 Worker 不继承请求线程的 Session 或 Principal。
需要在后台使用的数据，必须放入 Job 的类型化输入。

## 实现步骤

### 步骤 1：登记周期扫描

已有扫描方法可以直接登记为无记录 Job，不必为每个扫描类复制一组元数据方法。
`DirectJob.unrecorded` 接收 `JobKey`、定义版本、实例能力编码和一个 `Runnable`。

```java
/** 登记 Workspace 分享过期扫描，由具体应用定义。 */
@Configuration(proxyBeanMethods = false)
public class ShareExpiryJobConfiguration {

    @Bean
    DirectJob<Void, Void> shareExpiryScanJob(ShareExpiryScan scan) {
        return DirectJob.unrecorded(
                new JobKey("WORKSPACE/SHARE_EXPIRY/SCAN"),
                1,
                TreeCode.ofColon("JOB:WORKER"),
                scan::execute);
    }

    @Bean
    JobSchedule<Void, Void> shareExpiryScanSchedule(
            @Qualifier("shareExpiryScanJob") DirectJob<Void, Void> job) {
        return new JobSchedule<>(
                new ScheduleKey("WORKSPACE/SHARE_EXPIRY/SCAN"),
                1,
                job,
                null,
                new IntervalSchedule(Duration.ofSeconds(5), Duration.ofSeconds(5)),
                MisfirePolicy.FIRE_ONCE_NOW,
                OverlapPolicy.SKIP_IF_RUNNING);
    }
}
```

`ShareExpiryScan.execute()` 是无参数方法，由具体应用定义。

`JobSchedule` 的参数依次是计划键、定义版本、Job、固定输入、时间定义、`MisfirePolicy` 和 `OverlapPolicy`。
`IntervalSchedule` 的两个参数是间隔和首次延迟。
`CronSchedule` 是另一种 `ScheduleTiming`。

`MisfirePolicy` 有两个取值：

- `SKIP`：忽略已经错过的触发，等待下一次正常时间。
- `FIRE_ONCE_NOW`：恢复后立即补一次执行，不逐个补齐全部错过时间。

`OverlapPolicy` 有三个取值：`SKIP_IF_RUNNING`、`WAIT` 和 `ALLOW`。

当前实例必须启用 Job 声明的 `InstanceCapability`。
该能力同时指出 Job 使用哪个 `SystemPrincipal`。
`Job.requiredCapability()` 的默认值是 `JOB:WORKER`。

默认 `JobTarget.any()` 表示合格实例共同承担一份工作。
默认的固定工作键使同一 Job 在系统身份内保持单活。

### 步骤 2：定义可恢复的 Job

`DirectJob<I, O>` 声明输入输出类型、`JobKey`、定义版本、`RetryRule` 和一次 `execute(...)`。
需要同一对象互斥、不同对象并行时，覆盖 `workKey(input)`。

```java
/** 导出一个 Workspace 的内容并生成 Attachment，由具体应用定义。 */
@Component
public class WorkspaceExportJob extends DirectJob<Long, Long> {

    private final WorkspaceExporter exporter;

    public WorkspaceExportJob(WorkspaceExporter exporter) {
        this.exporter = exporter;
    }

    @Override
    public JobKey key() {
        return new JobKey("WORKSPACE/EXPORT");
    }

    @Override
    public int definitionVersion() {
        return 1;
    }

    @Override
    public Class<Long> inputType() {
        return Long.class;
    }

    @Override
    public Class<Long> outputType() {
        return Long.class;
    }

    @Override
    public RetryRule retryRule() {
        return RetryRule.atMost(3, backoff());
    }

    @Override
    public JobWorkKey workKey(Long workspaceId) {
        return JobWorkKey.of(TreeCode.ofColon("WORKSPACE:EXPORT"), workspaceId);
    }

    @Override
    protected Long execute(JobExecution execution, Long workspaceId) {
        // 返回生成的 Attachment 标识。重试会再次执行完整动作，导出必须按 workspaceId 幂等。
        return exporter.exportOnce(workspaceId);
    }
}
```

`backoff()` 返回 `RetryRule.atMost` 所需的 `Backoff`，由具体应用按需要构造。
`RetryRule` 的取值见 [重试模型](/capabilities/foundation/retry)。

`runRecording()` 默认是 `PERSISTED`，Run、节点、尝试和结果保存在 PostgreSQL。
Worker 失联后，其他实例可以重新领取过期节点。
因此业务动作必须能够处理重复执行。
提交、计划触发和广播共用同一个 `JobExecutor` 入口，是否持久化由 `runRecording()` 选择。

### 步骤 3：提交并查询

注入 `JobExecutor` 提交。
提交后检查 `JobSubmission`：

```java
/** 接收导出请求并提交 Job，由具体应用定义。 */
@Service
public class WorkspaceExportService {

    private final JobExecutor jobs;

    private final WorkspaceExportJob exportJob;

    public WorkspaceExportService(JobExecutor jobs, WorkspaceExportJob exportJob) {
        this.jobs = jobs;
        this.exportJob = exportJob;
    }

    public Optional<JobRunStatus> request(long workspaceId) {
        JobSubmission submission = jobs.submit(exportJob, workspaceId);
        if (submission.status() == JobSubmission.Status.SKIPPED) {
            return Optional.empty();
        }
        Id runId = submission.runId().orElseThrow();
        return Optional.of(jobs.status(runId));
    }
}
```

`JobSubmission` 的状态含义如下：

- `CREATED`：创建了 Run。
- `EXISTING`：同一输入已有活动 Run。
- `SKIPPED`：同键工作正在处理其他输入，此时没有本次 Run ID。

`JobRunStatus` 有 `QUEUED`、`RUNNING`、`SUCCEEDED` 和 `FAILED` 四个取值。
需要调用线程限时等待结果时，使用 `jobs.run(job, input, timeout)`。

### 步骤 4：选择执行目标

默认由任一合格实例执行一次。
需要由特定实例执行时，覆盖 `target(input)` 并返回 `JobTarget.instance(instanceId, wait)`。
需要每个在线合格实例各执行一次时，返回 `JobTarget.all(wait)`，并通过 `jobs.broadcast(job, input)` 取得广播 ID 和目标清单。
`wait` 必须大于零。
等待期限只限制目标开始工作，运行中的份额另有租约。
新上线实例不加入已经创建的广播。

### 步骤 5：拆分恢复点

多个业务步骤需要不同的恢复点时，使用 `PipelineJob`。
每条 Pipeline 对应一个持久化节点。
节点成功后，其输出才交给后继节点。
失败节点重试时，不会重跑已经成功的前置节点。
`DataGraph` 适合进程内只读数据装载，不保存可恢复节点。

## 扩展方式

应用把每个 Job 和每个计划声明为 Spring Bean。
框架在启动时收集并校验：

| 要接入的内容 | 应用实现 | 声明方式 | 框架发现与校验 |
|---|---|---|---|
| Job | `DirectJob` 或 `PipelineJob` 的子类，或 `DirectJob.unrecorded(...)` | Spring Bean | `JobCatalog` 通过 `ObjectProvider<Job<?, ?>>` 收集全部 Job。同一 `JobKey` 与定义版本重复时抛出 `IllegalStateException` |
| 周期计划 | `JobSchedule<I, O>` | Spring Bean | Quartz 调度器收集全部 `JobSchedule`，对账成功后才开始领取 trigger |
| 实例能力 | `InstanceCapability` | Spring Bean | `JobCatalog` 在筛选可用 Job 时要求 Job 声明的能力已经定义，否则抛出 `IllegalStateException` |

定义版本用于升级。
修改 Job 的输入输出结构时，提高 `definitionVersion` 并保留旧版本，旧 Run 才能按原版本恢复。
旧版本缺失时，恢复失败并报告「Job definitionVersion 不可恢复」。

## 验证

- 启动应用，确认没有 Job 或计划的校验异常。
- 步骤 1 的计划：观察 `ShareExpiryScan.execute()` 按 5 秒间隔被调用。无记录执行不提供 Run 状态查询，通过业务状态确认结果。
- 步骤 2 和步骤 3 的 Job：调用 `request(workspaceId)`，确认 `JobSubmission.Status.CREATED`。再次提交同一 `workspaceId`，确认返回 `EXISTING`。查询 `status(runId)`，直至 `SUCCEEDED`。
- 让 `exportOnce` 抛出异常，确认按 `RetryRule` 重试，次数耗尽后 Run 变为 `FAILED`。
- 多实例部署时，停止正在执行的 Worker，确认其他实例在租约过期后接管。

## 失败结果与限制

- `JobExecutor.submit` 在 Job 未登记、无记录、定义不一致、输入无效或目标为全部实例时抛出 `IllegalArgumentException`。
- `jobs.run` 超时抛出 `JobWaitTimeoutException`，超时不取消持久化 Run。Run 失败抛出 `JobRunFailedException`。同键其他输入正在执行、本次被跳过时抛出 `IllegalStateException`。
- `JobSchedule` 的 `definitionVersion` 不是正数、非 `Void` Job 的计划输入为空、或输入类型与 Job 不一致时，抛出 `IllegalArgumentException`。
- 无记录 `DirectJob` 的 `retryRule()` 不是 `RetryRule.once()` 时，启动抛出 `IllegalStateException`，消息为「无记录 Job 不提供框架重试」。
- 无记录 Job 的 `overlapPolicy()` 为 `WAIT` 时，启动抛出 `IllegalStateException`。
- `PipelineJob` 必须保存运行记录，且至少有一个节点，否则启动抛出 `IllegalStateException`。
- 当前实例未启用 Job 所需能力时，执行抛出 `IllegalStateException`，消息为「当前实例未启用 Job 所需能力」。
- Quartz 对账或启动失败时抛出 `SchedulingQuartzException`。持久化或定向的 `JobSchedule` 在没有 `JobExecutor` 时抛出 `IllegalStateException`。
- 重试可能再次执行完整业务动作。文件绑定、设备命令和外部请求，需要业务唯一约束或结果核对。
- 异步执行不继承提交线程的身份、Session、ThreadLocal 或事务。

## 相关页面

- [Job 与调度能力](/capabilities/jobs-scheduling/)
- [Job](/capabilities/jobs-scheduling/job)
- [Pipeline Job 调度](/capabilities/jobs-scheduling/pipeline-job-scheduling)
- [Quartz](/capabilities/jobs-scheduling/quartz)
- [发布消息与通知](/tutorials/messaging-and-notification)
- [文件生命周期](/tutorials/file-lifecycle)
- API：[JobExecutor](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/job/JobExecutor.html)、[JobScheduler](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/scheduling/JobScheduler.html)
