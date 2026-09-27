package com.sca4d.job.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.job.Job;
import com.sca4d.core.api.job.JobExecutor;
import com.sca4d.core.api.job.JobRunFailedException;
import com.sca4d.core.api.job.JobRunId;
import com.sca4d.core.api.job.JobRunStatus;
import com.sca4d.core.api.job.JobWaitTimeoutException;
import com.sca4d.core.api.scheduling.OverlapPolicy;
import com.sca4d.core.api.scheduling.ScheduleKey;
import com.sca4d.job.persistence.RunRecord;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;

/** 使用 PostgreSQL Job runtime 实现提交、查询、等待和结果恢复。 */
public final class DefaultJobExecutor implements JobExecutor {

    private static final long RESULT_POLL_NANOS = Duration.ofMillis(50).toNanos();

    private final @NotNull JobCatalog catalog;

    private final @NotNull JobRunService runs;

    private final @NotNull ObjectMapper objectMapper;

    /**
     * 创建使用当前应用 Job 目录和 PostgreSQL Repository 的执行器。
     *
     * @param catalog 当前应用登记的 Job 定义
     * @param runs 持有 Job run 事务的服务
     * @param objectMapper 脚手架统一 JSON 映射器
     */
    public DefaultJobExecutor(
            @NotNull JobCatalog catalog,
            @NotNull JobRunService runs,
            @NotNull ObjectMapper objectMapper) {
        this.catalog = Objects.requireNonNull(catalog, "JobCatalog 不能为空");
        this.runs = Objects.requireNonNull(runs, "JobRunService 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "Job ObjectMapper 不能为空");
    }

    @Override
    public <I, O> @NotNull JobRunId submit(@NotNull Job<I, O> job, @Nullable I input) {
        Job<I, O> registered = registered(job);
        return runs.create(registered, serializeInput(registered, input));
    }

    @Override
    public <I, O> @NotNull Optional<JobRunId> submitScheduled(
            @NotNull Job<I, O> job,
            @Nullable I input,
            @NotNull ScheduleKey scheduleKey,
            @NotNull Instant scheduledAt,
            @NotNull OverlapPolicy overlapPolicy) {
        Job<I, O> registered = registered(job);
        return runs.createScheduled(
                registered,
                serializeInput(registered, input),
                scheduleKey,
                scheduledAt,
                overlapPolicy);
    }

    @Override
    public <I, O> @Nullable O run(
            @NotNull Job<I, O> job,
            @Nullable I input,
            @NotNull Duration timeout) {
        JobRunId runId = submit(job, input);
        return await(runId, job, timeout);
    }

    @Override
    public @NotNull JobRunStatus status(@NotNull JobRunId runId) {
        return loadRunById(runId).status();
    }

    @Override
    public <O> @NotNull Optional<O> result(
            @NotNull JobRunId runId,
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
            @NotNull JobRunId runId,
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
    private @NotNull RunRecord loadRunById(@NotNull JobRunId runId) {
        JobRunId checked = Objects.requireNonNull(runId, "JobRunId 不能为空");
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
