package com.sca4d.job.persistence;

import com.sca4d.core.api.job.JobKey;
import com.sca4d.core.api.job.JobRunStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 保存 JobExecutor 查询状态和结果需要的根运行列值。 */
public class RunRecord {

    private final long id;

    private final @NotNull JobKey jobKey;

    private final int jobDefinitionVersion;

    private final @Nullable String outputJson;

    private final @NotNull JobRunStatus status;

    /**
     * 创建根运行查询结果。
     *
     * @param id 根运行数据库主键
     * @param jobKey 提交时保存的 Job key
     * @param jobDefinitionVersion 提交时保存的 Job 定义版本
     * @param outputJson 成功输出 JSON；未成功或 Void 输出时为空
     * @param status 当前根运行状态
     */
    public RunRecord(
            long id,
            @NotNull JobKey jobKey,
            int jobDefinitionVersion,
            @Nullable String outputJson,
            @NotNull JobRunStatus status) {
        this.id = id;
        this.jobKey = jobKey;
        this.jobDefinitionVersion = jobDefinitionVersion;
        this.outputJson = outputJson;
        this.status = status;
    }

    /**
     * 返回根运行主键。
     *
     * @return 根运行主键
     */
    public long id() {
        return id;
    }

    /**
     * 返回提交根运行时保存的 Job key。
     *
     * @return 提交时的 Job key
     */
    public @NotNull JobKey jobKey() {
        return jobKey;
    }

    /**
     * 返回提交根运行时保存的 Job 定义版本。
     *
     * @return 提交时的 Job 定义版本
     */
    public int jobDefinitionVersion() {
        return jobDefinitionVersion;
    }

    /**
     * 返回根运行成功后保存的输出 JSON。
     *
     * @return 成功输出 JSON；未成功或 Void 输出时为空
     */
    public @Nullable String outputJson() {
        return outputJson;
    }

    /**
     * 返回当前根运行状态。
     *
     * @return 当前根运行状态
     */
    public @NotNull JobRunStatus status() {
        return status;
    }
}
