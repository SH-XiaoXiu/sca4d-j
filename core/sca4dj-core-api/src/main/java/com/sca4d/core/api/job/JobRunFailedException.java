package com.sca4d.core.api.job;

import com.sca4d.core.api.id.Id;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 表明调用方等待的 Job run 已失败，错误详情保存在对应运行记录中。 */
public class JobRunFailedException extends RuntimeException {

    /** 进入 FAILED 状态的运行标识。 */
    private final @NotNull Id runId;

    /** 当前异常表示的终态，固定为 FAILED。 */
    private final @NotNull JobRunStatus status;

    /**
     * 创建终态失败异常。
     *
     * @param runId 失败的 run 标识
     * @param status FAILED
     * @throws NullPointerException run 标识或状态为空时抛出
     * @throws IllegalArgumentException 状态不是 {@link JobRunStatus#FAILED} 时抛出
     */
    public JobRunFailedException(@NotNull Id runId, @NotNull JobRunStatus status) {
        super(message(runId, status));
        if (status != JobRunStatus.FAILED) {
            throw new IllegalArgumentException("JobRunFailedException 只接受 FAILED 状态");
        }
        this.runId = runId;
        this.status = status;
    }

    /**
     * 失败的 run。
     *
     * @return 进入 FAILED 的运行标识
     */
    @Contract(pure = true)
    public @NotNull Id runId() {
        return runId;
    }

    /**
     * 异常表示的终态。
     *
     * @return 固定为 FAILED
     */
    @Contract(pure = true)
    public @NotNull JobRunStatus status() {
        return status;
    }

    /** 校验失败异常参数并创建不包含业务错误详情的消息。 */
    private static @NotNull String message(@NotNull Id runId, @NotNull JobRunStatus status) {
        return "Job run 未成功: runId=" + Objects.requireNonNull(runId, "Id 不能为空")
                + ", status=" + Objects.requireNonNull(status, "JobRunStatus 不能为空");
    }
}
