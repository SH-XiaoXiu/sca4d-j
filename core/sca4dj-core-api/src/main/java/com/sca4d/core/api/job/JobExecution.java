package com.sca4d.core.api.job;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 向 DirectJob 明确提供当前持久化运行和尝试事实。 */
public final class JobExecution {

    private final @NotNull JobRunId runId;

    private final int attempt;

    private final @NotNull Instant startedAt;

    /**
     * 创建一次节点尝试的执行事实。
     *
     * @param runId 所属持久化 run
     * @param attempt 从 1 开始的尝试次数
     * @param startedAt 当前尝试开始时间
     * @throws NullPointerException run 标识、节点名称或开始时间为空时抛出
     * @throws IllegalArgumentException 节点名称为空或尝试次数小于 1 时抛出
     */
    public JobExecution(
            @NotNull JobRunId runId,
            int attempt,
            @NotNull Instant startedAt) {
        this.runId = Objects.requireNonNull(runId, "Job run 标识不能为空");
        if (attempt < 1) {
            throw new IllegalArgumentException("Job 尝试次数不能小于 1");
        }
        this.attempt = attempt;
        this.startedAt = Objects.requireNonNull(startedAt, "Job 开始时间不能为空");
    }

    /**
     * 返回所属持久化 run。
     *
     * @return 当前尝试所属的 run 标识
     */
    @Contract(pure = true)
    public @NotNull JobRunId runId() {
        return runId;
    }

    /**
     * 返回当前尝试序号。
     *
     * @return 从 1 开始的尝试次数
     */
    @Contract(pure = true)
    public int attempt() {
        return attempt;
    }

    /**
     * 返回当前尝试开始时间。
     *
     * @return Job runtime 开始本次尝试时使用的时间
     */
    @Contract(pure = true)
    public @NotNull Instant startedAt() {
        return startedAt;
    }
}
