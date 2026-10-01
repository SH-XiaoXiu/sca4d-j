package com.sca4d.core.api.job;

import com.sca4d.core.api.id.Id;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * 返回一次定时 Job run 的状态和调用方选择的类型化节点输出。
 *
 * <p>根运行尚未结束或目标节点没有成功时，节点输出为空。根运行失败时，较早成功节点的输出仍可存在。
 *
 * @param <T> 查询选择的节点输出类型
 */
@SuppressWarnings("ClassCanBeRecord") // 公共类型遵守项目禁用 record 的兼容规则。
public class ScheduledJobRun<T> {

    private final @NotNull Id runId;

    private final @Nullable Instant completedAt;

    private final @NotNull Instant scheduledAt;

    private final @NotNull JobRunStatus status;

    private final @Nullable T output;

    public ScheduledJobRun(
            @NotNull Id runId,
            @NotNull Instant scheduledAt,
            @NotNull JobRunStatus status,
            @Nullable Instant completedAt,
            @Nullable T output) {
        this.runId = Objects.requireNonNull(runId, "Job run 标识不能为空");
        this.scheduledAt = Objects.requireNonNull(scheduledAt, "Job 原定触发时间不能为空");
        this.status = Objects.requireNonNull(status, "Job run 状态不能为空");
        this.completedAt = completedAt;
        this.output = output;
    }

    /** @return Job run 标识 */
    @Contract(pure = true)
    public @NotNull Id runId() {
        return runId;
    }

    /** @return Scheduler 记录的原定触发时间 */
    @Contract(pure = true)
    public @NotNull Instant scheduledAt() {
        return scheduledAt;
    }

    /** @return 根 Job run 当前状态 */
    @Contract(pure = true)
    public @NotNull JobRunStatus status() {
        return status;
    }

    /** @return 根 Job run 完成时间；仍在排队或运行时为空 */
    @Contract(pure = true)
    public @Nullable Instant completedAt() {
        return completedAt;
    }

    /** @return 目标节点成功时保存的输出；节点没有成功时为空 */
    @Contract(pure = true)
    public @Nullable T output() {
        return output;
    }
}
