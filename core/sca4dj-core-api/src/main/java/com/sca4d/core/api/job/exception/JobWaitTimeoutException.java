package com.sca4d.core.api.job.exception;

import com.sca4d.core.api.id.Id;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Objects;

/** 表明调用方等待时间结束，持久化 Job run 仍继续执行。 */
public class JobWaitTimeoutException extends RuntimeException {

    /** 触发当前异常的等待上限。 */
    private final @NotNull Duration timeout;

    /** 等待结束时仍未完成的运行标识。 */
    private final @NotNull Id runId;

    /**
     * 创建不取消后台运行的等待超时异常。
     *
     * @param runId 等待时间结束时仍未完成的 run
     * @param timeout 本次调用使用的正数等待时长
     * @throws NullPointerException run 标识或等待时长为空时抛出
     */
    public JobWaitTimeoutException(@NotNull Id runId, @NotNull Duration timeout) {
        super("等待 Job run 超时: runId=" + runId + ", timeout=" + timeout);
        this.runId = Objects.requireNonNull(runId, "Job run 标识不能为空");
        this.timeout = Objects.requireNonNull(timeout, "Job 等待时长不能为空");
    }

    /**
     * 尚未完成的 run。
     *
     * @return 等待超时时仍可继续查询的运行标识
     */
    @Contract(pure = true)
    public @NotNull Id runId() {
        return runId;
    }

    /**
     * 本次等待上限。
     *
     * @return 触发该异常的等待时长
     */
    @Contract(pure = true)
    public @NotNull Duration timeout() {
        return timeout;
    }
}
