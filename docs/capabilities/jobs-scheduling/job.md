# Job 模块

`sca4dj-job` 执行代码登记的 [Job](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/job/Job.html)。后台执行始终使用系统主体。Job 声明所需的 `InstanceCapability`，运行时从该能力取得 `SystemPrincipal`，为每次执行建立独立的 `Call.system(...)`。人工发起人的身份属于业务请求来源，不会从提交线程传给 Worker。实际执行的实例由 `InstanceId` 标识。`InstanceId` 不参与 IAM 权限判定。

## 定义与提交

`DirectJob` 把完整动作作为一个执行单元。`PipelineJob` 在 Pipeline 之间保存可恢复的节点和依赖，不能使用无记录模式。

两者沿用稳定 `JobKey`、`definitionVersion`、输入类型和输出类型。同一 key 可以登记多个定义版本。Worker 只使用 Run 提交时保存的精确版本。

`Job.workKey(input)` 默认对同一 Job 返回固定键。业务需要按对象并行时，返回相应对象的 `JobWorkKey`。

普通提交的默认 `OverlapPolicy.SKIP_IF_RUNNING` 按应用、系统身份、Job key、工作键和目标实例判断活动工作。手动提交返回 `JobSubmission`：

| 状态 | 含义 |
|---|---|
| `CREATED` | 创建了本次 Run |
| `EXISTING` | 相同定义版本、相同输入的同键 Run 仍在执行，返回其 ID |
| `SKIPPED` | 同键 Run 正在处理其他输入或定义版本，本次不创建 Run |

`WAIT` 保存另一份 Run，按提交先后执行同键工作。`ALLOW` 允许重叠执行。`JobExecutor.run(...)` 在 `SKIPPED` 时拒绝等待其他输入的结果。定时提交另按 `ScheduleKey` 和 Quartz 原定触发时间去重。

工作键只控制重叠，不代替业务唯一约束和外部系统幂等键。

## 目标实例

`Job.target(input)` 默认使用 `JobTarget.any()`。所有启用所需能力的实例共同领取，一份 Run 同时只有一个有效租约。

`JobTarget.instance(instanceId, wait)` 把工作绑定到指定实例。工作在期限内等待该实例。目标过期前仍未启动，则失败。

`JobTarget.all(wait)` 在触发时固定在线且启用所需能力的实例清单，并为每个实例创建一份工作。触发后新上线的实例从下次广播参与。广播份额不能由其他实例接管。

Job 模块在 `job.job_instance` 中保存实例 ID、启动代次、启用能力和在线租约。该登记独立于 Gateway 的进程内注册表。

实例重启会生成新代次。已固定代次的广播份额不冒充新进程。显式指定实例且未固定代次的工作，允许同一配置实例 ID 在期限内重新登记后领取。

广播的 `JobDispatch` 使用数据库分配的 `Id` 标识本次目标快照，并返回目标集合及已创建的 Run。目标已有活动工作且策略为 `SKIP_IF_RUNNING` 时，目标仍保留在快照中，但不创建新的份额。没有合格目标的广播会失败。

## 运行记录与恢复

| 策略 | 保存内容 | 恢复方式 |
|---|---|---|
| `PERSISTED` | `job_run`、节点、依赖和尝试 | Worker 续租、接管、节点重试；可以查询状态和输出 |
| `NONE` | 不保存 Job Run | 下次触发或业务状态再次发现；不提供 Run 查询和框架重试 |

`Job.runRecording()` 的默认值为 `PERSISTED`。

`NONE` 的普通定时工作，在 Quartz 实例具备执行能力时直接运行。否则，选择一个在线合格实例创建短期投递。

指定实例和广播也通过 `job.job_dispatch` 固定目标，由 `job.job_delivery` 协调份额。份额只有一次执行尝试，过期后清理，不属于 Job Run 历史。`NONE + WAIT` 和 `PipelineJob + NONE` 不允许登记。

`PERSISTED` 的根状态为 `QUEUED`、`RUNNING`、`SUCCEEDED` 或 `FAILED`。

节点通过数据库条件更新领取，并按租约续期。旧 Worker 的写回必须匹配 Worker ID 和尝试次数。

同一 PipelineJob 的节点成功推进，在根 Run 行锁下串行化。两个并发分支同时完成时，也会激活合并节点。

节点外部操作完成后，Worker 仍可能在保存结果前退出。因此，业务副作用仍按至少一次执行处理。

无记录投递的目标等待期限只限制尚未开始的份额。开始后另行续租。运行中的份额失去租约会被标为失败，不转交给其他实例。进程退出后已产生的外部副作用仍需业务方核对。

## 节点重试

`DirectJob` 通过 `retryRule()` 声明 `RetryRule`。`PipelineJob` 在每个节点声明规则。

节点创建时，将完整规则冻结到 `retry_rule`。规则支持固定、指数、序列退避和无限重试。次数包含首次执行。`RetryRule.once()` 禁止框架重试。无记录 Job 只接受 `once()`。

失败后，由统一的重试策略计算等待时长。Job runtime 保存 `available_at`。

`max_attempts` 由规则派生。NULL 表示无限。节点与历史尝试使用 long 序号。部署修改规则，不改变已保存的节点快照。

Worker 租约过期时，原尝试标记为 `ABANDONED`。未耗尽时，以原租约截止时间加退避计算下一次时间。已经到期的节点可以立即领取。重复扫描不重新延后。耗尽时，结束节点和根运行，并取消等待中的后继节点。

规则和同步调用示例见[重试规则与执行](/capabilities/foundation/retry)。同一节点不要默认叠加同步重试。事务与副作用仍由节点操作负责。

## 数据归属与历史

`job.job_run` 保存应用隔离名称、Job 版本、系统身份、工作键、目标、输入输出及根状态。`job.job_run_node`、`job.job_run_dependency` 和 `job.job_run_attempt` 保存恢复所需的节点事实。

`job.job_instance`、`job.job_dispatch` 和 `job.job_delivery` 保存实例可用性与短期投递信息。Quartz 的 `quartz.qrtz_*` 只保存时间计划和集群调度状态。

`JobHistory.findScheduledRuns(...)` 按当前应用、计划、Job key 和明确的定义版本查询类型化节点输出。成功 Run 默认保留 30 天。FAILED Run 不按此窗口清理。

需要长期保存的业务状态，由具体应用的业务表负责。它们不依赖 Job 历史保留策略。

Job 模块只访问当前 `spring.application.name` 对应的应用隔离名称。改变应用名称不会迁移旧 Run、广播或 Quartz 计划。

## 示例

以下 DirectJob 为 Workspace 导出生成一份持久化 Run。该 Job 的输入为 Workspace 标识，输出为导出文件数量。省略 import 语句。

```java
// 输入：Workspace 标识与编码。
public record WorkspaceExportInput(long workspaceId, String workspaceCode) {
}

// 由具体应用定义的 DirectJob。
public final class WorkspaceExportJob extends DirectJob<WorkspaceExportInput, Integer> {

    private final ExportService exports;

    public WorkspaceExportJob(ExportService exports) {
        this.exports = exports;
    }

    @Override
    public JobKey key() {
        return new JobKey("WORKSPACE/EXPORT/GENERATE");
    }

    @Override
    public int definitionVersion() {
        return 1;
    }

    @Override
    public Class<WorkspaceExportInput> inputType() {
        return WorkspaceExportInput.class;
    }

    @Override
    public Class<Integer> outputType() {
        return Integer.class;
    }

    @Override
    public RetryRule retryRule() {
        // 首次执行后最多再重试 2 次，退避从 30 秒开始按 2 倍增长，上限 10 分钟。
        return RetryRule.atMost(3, Backoff.exponential(Duration.ofSeconds(30), 2, Duration.ofMinutes(10)));
    }

    @Override
    public JobWorkKey workKey(WorkspaceExportInput input) {
        // 同一 Workspace 的导出共享并发边界。
        return JobWorkKey.of(input.workspaceCode());
    }

    @Override
    protected Integer execute(JobExecution execution, WorkspaceExportInput input) {
        // 导出结果写入业务表时使用唯一约束或状态条件，防止重试产生重复记录。
        return exports.generate(input.workspaceId());
    }
}
```

提交与广播的调用方式如下。`JobExecutor` 由框架注入。

```java
// 由具体应用定义的服务。
@Service
class WorkspaceExportService {

    private final JobExecutor jobs;
    private final WorkspaceExportJob exportJob;

    WorkspaceExportService(JobExecutor jobs, WorkspaceExportJob exportJob) {
        this.jobs = jobs;
        this.exportJob = exportJob;
    }

    public Optional<Id> submit(long workspaceId, String workspaceCode) {
        JobSubmission submission = jobs.submit(exportJob, new WorkspaceExportInput(workspaceId, workspaceCode));
        // CREATED 与 EXISTING 都返回 Run 标识；SKIPPED 不返回。
        return submission.runId();
    }
}
```

`Job.target(input)` 默认返回 `JobTarget.any()`，任一合格实例领取。需要在所有在线实例上执行时，覆盖 `target(input)` 返回 `JobTarget.all(Duration)`，或调用 `JobExecutor.broadcast(job, input)`。

Job 的注册方式见 [Quartz 调度模块](/capabilities/jobs-scheduling/quartz) 中的计划说明。Job 实例由 Spring 收集，`JobCatalog` 按 `JobKey` 与 `definitionVersion` 建立目录，同一组合重复时启动失败。

## 扩展方式

应用提供 `Job<I, O>` 的子类，并注册为 Spring Bean。`JobCatalog` 通过 `ObjectProvider<Job<?, ?>>` 收集全部 Job。`JobKey` 与 `definitionVersion` 的组合重复时，目录创建失败。

`requiredCapability()` 默认返回 `JOB:WORKER`。应用需要其他能力时覆盖该方法，并为实例配置对应的 `InstanceCapability`。

`PipelineJob` 的节点规则与依赖由 `plan()` 声明，见[Pipeline、Job 与定时调度](/capabilities/jobs-scheduling/pipeline-job-scheduling)。

## 相关页面

- [Pipeline、Job 与定时调度](/capabilities/jobs-scheduling/pipeline-job-scheduling)
- [Job 配置](/capabilities/jobs-scheduling/job-configuration)
- [Quartz 调度模块](/capabilities/jobs-scheduling/quartz)
- [流程状态机](/capabilities/jobs-scheduling/flow-state-machine)
- [重试规则与执行](/capabilities/foundation/retry)
