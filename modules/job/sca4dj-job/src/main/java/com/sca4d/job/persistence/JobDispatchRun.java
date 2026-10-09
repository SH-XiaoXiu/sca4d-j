package com.sca4d.job.persistence;

import com.sca4d.core.api.runtime.InstanceId;
import org.jetbrains.annotations.NotNull;

/** 一次广播下某个目标实例已创建的持久化 Run。 */
public class JobDispatchRun {

    private final @NotNull InstanceId instanceId;

    private final long runId;

    /**
     * 创建广播目标与持久化运行的关联。
     *
     * @param instanceId 广播固定的目标实例
     * @param runId 该目标创建的 Run 标识
     */
    public JobDispatchRun(@NotNull String instanceId, long runId) {
        this.instanceId = new InstanceId(instanceId);
        this.runId = runId;
    }

    /** @return 本次广播固定的目标实例 */
    public @NotNull InstanceId instanceId() {
        return instanceId;
    }

    /** @return 该目标创建的 Run 标识 */
    public long runId() {
        return runId;
    }
}
