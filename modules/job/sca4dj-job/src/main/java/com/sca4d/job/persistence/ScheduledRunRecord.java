package com.sca4d.job.persistence;

import com.sca4d.core.api.job.JobRunStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/** 保存定时 Job 历史查询从根运行和目标节点读取的数据库列。 */
@SuppressWarnings("ClassCanBeRecord") // MyBatis 构造映射遵守项目禁用 record 的规则。
public final class ScheduledRunRecord {

    private final long runId;

    private final @NotNull Instant scheduledAt;

    private final @NotNull JobRunStatus status;

    private final @Nullable Instant completedAt;

    private final @Nullable String outputJson;

    /** 创建一条尚未向公共 API 转换的定时运行查询结果。 */
    public ScheduledRunRecord(
            long runId,
            @NotNull Instant scheduledAt,
            @NotNull JobRunStatus status,
            @Nullable Instant completedAt,
            @Nullable String outputJson) {
        this.runId = runId;
        this.scheduledAt = Objects.requireNonNull(scheduledAt, "Job 原定触发时间不能为空");
        this.status = Objects.requireNonNull(status, "Job run 状态不能为空");
        this.completedAt = completedAt;
        this.outputJson = outputJson;
    }

    /** @return 根运行数据库主键 */
    @Contract(pure = true)
    public long runId() {
        return runId;
    }

    /** @return Scheduler 记录的原定触发时间 */
    @Contract(pure = true)
    public @NotNull Instant scheduledAt() {
        return scheduledAt;
    }

    /** @return 根运行当前状态 */
    @Contract(pure = true)
    public @NotNull JobRunStatus status() {
        return status;
    }

    /** @return 根运行完成时间；仍在处理时为空 */
    @Contract(pure = true)
    public @Nullable Instant completedAt() {
        return completedAt;
    }

    /** @return 目标节点成功输出 JSON；节点尚未成功时为空 */
    @Contract(pure = true)
    public @Nullable String outputJson() {
        return outputJson;
    }
}
