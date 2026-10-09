package com.sca4d.job.runtime;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 由集群定时器触发一次成功 Job run 历史清理。 */
public final class JobHistoryCleanupTask {

    private final @NotNull JobHistoryService history;

    /**
     * 创建 Job 成功历史清理任务。
     *
     * @param history 按保留时间清理成功根运行的服务
     */
    public JobHistoryCleanupTask(@NotNull JobHistoryService history) {
        this.history = Objects.requireNonNull(history, "JobHistoryService 不能为空");
    }

    /** 按 Job 历史保留策略清理已成功的运行。 */
    public void execute() {
        history.cleanupSucceededRuns();
    }
}
