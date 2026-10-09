package com.sca4d.job.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.identity.SystemPrincipals;
import com.sca4d.core.api.job.DirectJob;
import com.sca4d.core.api.job.Job;
import com.sca4d.core.api.job.JobDispatch;
import com.sca4d.core.api.job.JobExecution;
import com.sca4d.core.api.job.JobExecutor;
import com.sca4d.core.api.job.JobRunFailedException;
import com.sca4d.core.api.job.JobRunRecording;
import com.sca4d.core.api.job.JobRunStatus;
import com.sca4d.core.api.job.JobSubmission;
import com.sca4d.core.api.job.JobTarget;
import com.sca4d.core.api.job.JobWaitTimeoutException;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.runtime.InstanceCapabilities;
import com.sca4d.core.api.runtime.InstanceId;
import com.sca4d.core.api.scheduling.OverlapPolicy;
import com.sca4d.core.api.scheduling.ScheduleKey;
import com.sca4d.core.spring.call.CallHolder;
import com.sca4d.core.spring.config.InstanceProperties;
import com.sca4d.core.spring.json.Sca4djMapper;
import com.sca4d.job.config.JobProperties;
import com.sca4d.job.persistence.RunRecord;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/** 使用 PostgreSQL Job runtime 实现提交、查询、等待和结果恢复。 */
public class DefaultJobExecutor implements JobExecutor {

    private static final long RESULT_POLL_NANOS = Duration.ofMillis(50).toNanos();

    private final @NotNull JobRunService runs;

    private final @NotNull Sca4djMapper objectMapper;

    private final @NotNull InstanceProperties instanceProperties;

    private final @NotNull JobProperties properties;

    private final @NotNull Clock clock;

    private final @NotNull DistributedLock distributedLock;

    private final @NotNull InstanceCapabilities instanceCapabilities;

    private final @NotNull JobCatalog catalog;

    /** 创建同时支持持久化 Job 和无记录系统任务的执行器。 */
    public DefaultJobExecutor(
            @NotNull JobCatalog catalog,
            @NotNull JobRunService runs,
            @NotNull Sca4djMapper objectMapper,
            @NotNull InstanceCapabilities instanceCapabilities,
            @NotNull InstanceProperties instanceProperties,
            @NotNull DistributedLock distributedLock,
            @NotNull Clock clock,
            @NotNull JobProperties properties) {
        this.catalog = Objects.requireNonNull(catalog, "JobCatalog 不能为空");
        this.runs = Objects.requireNonNull(runs, "JobRunService 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "Job ObjectMapper 不能为空");
        this.instanceCapabilities = Objects.requireNonNull(instanceCapabilities, "Job 实例能力不能为空");
        this.instanceProperties = Objects.requireNonNull(instanceProperties, "Job 实例配置不能为空");
        this.distributedLock = Objects.requireNonNull(distributedLock, "Job 分布式锁不能为空");
        this.clock = Objects.requireNonNull(clock, "Job 时钟不能为空");
        this.properties = Objects.requireNonNull(properties, "Job 配置不能为空");
    }

    @Override
    public <I, O> @NotNull JobSubmission submit(@NotNull Job<I, O> job, @Nullable I input) {
        Job<I, O> registered = registered(job);
        requirePersisted(registered);
        JobTarget target = Objects.requireNonNull(registered.target(input), "Job 目标不能为空");
        if (target.kind() == JobTarget.Kind.ALL) {
            throw new IllegalArgumentException("广播 Job 请使用 broadcast 入口: " + registered.key());
        }
        return runs.create(
                registered,
                serializeInput(registered, input),
                systemIdentity(registered),
                Objects.requireNonNull(registered.workKey(input), "Job 工作键不能为空"),
                registered.overlapPolicy(),
                target);
    }

    @Override
    public <I, O> @NotNull Optional<Id> submitScheduled(
            @NotNull Job<I, O> job,
            @Nullable I input,
            @NotNull ScheduleKey scheduleKey,
            @NotNull Instant scheduledAt,
            @NotNull OverlapPolicy overlapPolicy) {
        Job<I, O> registered = registered(job);
        requirePersisted(registered);
        JobTarget target = Objects.requireNonNull(registered.target(input), "Job 目标不能为空");
        if (target.kind() == JobTarget.Kind.ALL) {
            throw new IllegalArgumentException("广播定时 Job 请使用 broadcastScheduled 入口: " + registered.key());
        }
        return runs.createScheduled(
                registered,
                serializeInput(registered, input),
                scheduleKey,
                scheduledAt,
                overlapPolicy,
                systemIdentity(registered),
                Objects.requireNonNull(registered.workKey(input), "Job 工作键不能为空"),
                target);
    }

    @Override
    public <I, O> @NotNull JobDispatch broadcast(@NotNull Job<I, O> job, @Nullable I input) {
        Job<I, O> registered = registered(job);
        JobTarget target = Objects.requireNonNull(registered.target(input), "Job 目标不能为空");
        if (target.kind() != JobTarget.Kind.ALL) {
            throw new IllegalArgumentException("Job 未声明向全部在线实例广播: " + registered.key());
        }
        return runs.dispatch(
                registered,
                serializeInput(registered, input),
                systemIdentity(registered),
                Objects.requireNonNull(registered.workKey(input), "Job 工作键不能为空"),
                registered.overlapPolicy(),
                target,
                null,
                null);
    }

    @Override
    public <I, O> @NotNull JobDispatch broadcastScheduled(
            @NotNull Job<I, O> job,
            @Nullable I input,
            @NotNull ScheduleKey scheduleKey,
            @NotNull Instant scheduledAt,
            @NotNull OverlapPolicy overlapPolicy) {
        Job<I, O> registered = registered(job);
        JobTarget target = Objects.requireNonNull(registered.target(input), "Job 目标不能为空");
        if (target.kind() != JobTarget.Kind.ALL) {
            throw new IllegalArgumentException("Job 未声明向全部在线实例广播: " + registered.key());
        }
        return runs.dispatch(
                registered,
                serializeInput(registered, input),
                systemIdentity(registered),
                Objects.requireNonNull(registered.workKey(input), "Job 工作键不能为空"),
                overlapPolicy,
                target,
                scheduleKey,
                scheduledAt);
    }

    @Override
    public <I, O> boolean trigger(
            @NotNull Job<I, O> job,
            @Nullable I input,
            @NotNull OverlapPolicy overlapPolicy) {
        return trigger(job, input, null, null, overlapPolicy);
    }

    @Override
    public <I, O> boolean triggerScheduled(
            @NotNull Job<I, O> job,
            @Nullable I input,
            @NotNull ScheduleKey scheduleKey,
            @NotNull Instant scheduledAt,
            @NotNull OverlapPolicy overlapPolicy) {
        return trigger(job, input, scheduleKey, scheduledAt, overlapPolicy);
    }

    private <I, O> boolean trigger(
            @NotNull Job<I, O> job,
            @Nullable I input,
            @Nullable ScheduleKey scheduleKey,
            @Nullable Instant scheduledAt,
            @NotNull OverlapPolicy overlapPolicy) {
        Job<I, O> registered = registered(job);
        if (registered.runRecording() != JobRunRecording.NONE
                || !(registered instanceof DirectJob<?, ?> directJob)) {
            throw new IllegalArgumentException("只有无记录 DirectJob 可以直接触发: " + registered.key());
        }
        if (overlapPolicy == OverlapPolicy.WAIT) {
            throw new IllegalArgumentException("无记录 Job 不支持等待执行: " + registered.key());
        }
        String inputJson = serializeInput(registered, input);
        JobTarget target = Objects.requireNonNull(registered.target(input), "Job 目标不能为空");
        if (target.kind() != JobTarget.Kind.ANY) {
            runs.dispatch(
                    registered,
                    inputJson,
                    systemIdentity(registered),
                    Objects.requireNonNull(registered.workKey(input), "Job 工作键不能为空"),
                    overlapPolicy,
                    target,
                    scheduleKey,
                    scheduledAt);
            return true;
        }
        var workKey = Objects.requireNonNull(registered.workKey(input), "Job 工作键不能为空");
        InstanceCapabilities capabilities = instanceCapabilities;
        var capability = capabilities.definitions().get(registered.requiredCapability());
        if (capability == null) {
            throw new IllegalStateException("Job 所需能力未登记: " + registered.requiredCapability());
        }
        if (!capabilities.enabled(registered.requiredCapability())) {
            var eligible = runs.eligibleInstances(
                    registered.requiredCapability().format(com.sca4d.core.api.name.NameFormat.COLON));
            if (eligible.isEmpty()) {
                throw new IllegalStateException("没有在线实例启用 Job 所需能力: " + registered.requiredCapability());
            }
            runs.dispatch(
                    registered,
                    inputJson,
                    capability.systemIdentity(),
                    workKey,
                    overlapPolicy,
                    JobTarget.instance(eligible.getFirst().instanceId(), properties.getLeaseDuration()),
                    scheduleKey,
                    scheduledAt);
            return true;
        }
        var principal = SystemPrincipals.require(capability.systemIdentity());
        var lockKey = JobRunService.workLock(registered, principal.code(), workKey);
        if (overlapPolicy == OverlapPolicy.ALLOW) {
            runUnrecorded(directJob, input, principal, registered.requiredCapability());
            return true;
        }
        AtomicBoolean entered = new AtomicBoolean();
        try {
            return distributedLock
                    .execute(lockKey, Duration.ZERO, () -> {
                        entered.set(true);
                        runUnrecorded(directJob, input, principal, registered.requiredCapability());
                        return true;
                    });
        } catch (BusinessException exception) {
            if (!entered.get() && exception.errorCode() == CoreErrorCode.CONFLICT) {
                return false;
            }
            throw exception;
        }
    }

    @SuppressWarnings("unchecked")
    private <I, O> void runUnrecorded(
            @NotNull DirectJob<?, ?> job,
            @Nullable I input,
            @NotNull com.sca4d.core.api.identity.SystemPrincipal principal,
            @NotNull TreeCode capability) {
        UUID executionId = UUID.randomUUID();
        Instant startedAt = clock.instant();
        var execution = new JobExecution(
                executionId,
                null,
                1,
                startedAt,
                new InstanceId(instanceProperties.getId()),
                capability,
                principal);
        try {
            CallHolder.run(
                    Call.system(executionId.toString(), startedAt, principal),
                    () -> ((DirectJob<I, O>) job).run(execution, input));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("无记录 Job 执行失败: " + job.key(), exception);
        }
    }

    private static void requirePersisted(@NotNull Job<?, ?> job) {
        if (job.runRecording() != JobRunRecording.PERSISTED) {
            throw new IllegalArgumentException("无记录 Job 不支持持久化提交: " + job.key());
        }
    }

    /** 从已声明实例能力解析稳定系统身份；提交方不必启用执行能力。 */
    private @NotNull TreeCode systemIdentity(@NotNull Job<?, ?> job) {
        var capability = instanceCapabilities.definitions().get(job.requiredCapability());
        if (capability == null) {
            throw new IllegalArgumentException("Job 所需实例能力未声明: " + job.requiredCapability());
        }
        return capability.systemIdentity();
    }

    @Override
    public <I, O> @Nullable O run(
            @NotNull Job<I, O> job,
            @Nullable I input,
            @NotNull Duration timeout) {
        Id runId = submit(job, input).runId()
                .orElseThrow(() -> new IllegalStateException("同键其他 Job 仍在执行: " + job.key()));
        return await(runId, job, timeout);
    }

    @Override
    public @NotNull JobRunStatus status(@NotNull Id runId) {
        return loadRunById(runId).status();
    }

    @Override
    public <O> @NotNull Optional<O> result(
            @NotNull Id runId,
            @NotNull Job<?, O> job) {
        Job<?, O> registered = registered(job);
        RunRecord run = loadRunById(runId);
        validateDefinition(run, registered);
        if (run.status() == JobRunStatus.FAILED) {
            throw new JobRunFailedException(runId, run.status());
        }
        if (run.status() != JobRunStatus.SUCCEEDED || Void.class.equals(registered.outputType())) {
            return Optional.empty();
        }
        return Optional.of(deserialize(run.outputJson(), registered.outputType()));
    }

    @Override
    public <O> @Nullable O await(
            @NotNull Id runId,
            @NotNull Job<?, O> job,
            @NotNull Duration timeout) {
        Duration checkedTimeout = validateTimeout(timeout);
        long timeoutNanos;
        try {
            timeoutNanos = checkedTimeout.toNanos();
        } catch (ArithmeticException exception) {
            timeoutNanos = Long.MAX_VALUE;
        }
        long started = System.nanoTime();
        while (true) {
            // 每轮重新读取根运行，校验调用方提供的 Job 仍与提交时的 key 和定义版本一致。
            RunRecord run = loadRunById(runId);
            Job<?, O> registered = registered(job);
            validateDefinition(run, registered);
            if (run.status() == JobRunStatus.FAILED) {
                throw new JobRunFailedException(runId, run.status());
            }
            if (run.status() == JobRunStatus.SUCCEEDED) {
                if (Void.class.equals(registered.outputType())) {
                    return null;
                }
                return deserialize(run.outputJson(), registered.outputType());
            }
            long elapsed = System.nanoTime() - started;

            // 超时只结束当前等待；持久化 run 保持原状态，由 worker 继续推进。
            if (elapsed >= timeoutNanos) {
                throw new JobWaitTimeoutException(runId, checkedTimeout);
            }
            LockSupport.parkNanos(Math.min(RESULT_POLL_NANOS, timeoutNanos - elapsed));

            // 中断不会取消 run，但调用线程必须立即恢复中断标记并退出等待。
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待 Job run 时线程被中断: " + runId);
            }
        }
    }

    /** 按 key 和定义版本返回 Spring 登记的定义，并校验调用方看到的类型。 */
    @SuppressWarnings("unchecked")
    private <I, O> @NotNull Job<I, O> registered(@NotNull Job<I, O> requested) {
        Job<I, O> checked = Objects.requireNonNull(requested, "Job 不能为空");
        Job<?, ?> found = catalog.loadByKeyAndVersion(checked.key(), checked.definitionVersion());
        if (!found.inputType().equals(checked.inputType()) || !found.outputType().equals(checked.outputType())) {
            throw new IllegalArgumentException("Job 类型与登记定义不一致: " + checked.key());
        }
        return (Job<I, O>) found;
    }

    /** 序列化非 Void 输入，并拒绝运行时类型不一致。 */
    private <I> @Nullable String serializeInput(@NotNull Job<I, ?> job, @Nullable I input) {
        if (input == null) {
            if (!Void.class.equals(job.inputType())) {
                throw new IllegalArgumentException("非 Void Job 输入不能为空: " + job.key());
            }
            return null;
        }
        if (!job.inputType().isInstance(input)) {
            throw new IllegalArgumentException("Job 输入类型不一致: " + job.key());
        }
        try {
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Job 输入无法序列化: " + job.key(), exception);
        }
    }

    /** 从 JSON 恢复具体类型，缺失成功输出视为损坏的运行记录。 */
    private <T> @NotNull T deserialize(@Nullable String json, @NotNull Class<T> type) {
        if (json == null) {
            throw new IllegalStateException("Job 输出缺失: " + type.getName());
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Job 输出无法恢复: " + type.getName(), exception);
        }
    }

    /** 返回存在的根运行。 */
    private @NotNull RunRecord loadRunById(@NotNull Id runId) {
        Id checked = Objects.requireNonNull(runId, "Id 不能为空");
        RunRecord run = runs.findRun(checked);
        if (run == null) {
            throw new IllegalArgumentException("Job run 不存在: " + checked);
        }
        return run;
    }

    /** 拒绝用不同 key 或定义版本读取已有 run。 */
    private static void validateDefinition(@NotNull RunRecord run, @NotNull Job<?, ?> job) {
        if (!run.jobKey().equals(job.key())
                || run.jobDefinitionVersion() != job.definitionVersion()) {
            throw new IllegalArgumentException("Job run 与定义不匹配: runId=" + run.id());
        }
    }

    /** 校验同步等待必须显式提供正数超时。 */
    private static @NotNull Duration validateTimeout(@NotNull Duration timeout) {
        Duration checked = Objects.requireNonNull(timeout, "Job 等待超时不能为空");
        if (checked.isZero() || checked.isNegative()) {
            throw new IllegalArgumentException("Job 等待超时必须大于零");
        }
        return checked;
    }
}
