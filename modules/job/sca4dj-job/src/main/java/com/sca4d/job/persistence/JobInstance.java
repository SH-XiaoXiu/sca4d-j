package com.sca4d.job.persistence;

import com.sca4d.core.api.runtime.InstanceId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** 当前在线且启用所需 capability 的进程实例及其本次启动代次。 */
public class JobInstance {

    private final @NotNull InstanceId instanceId;

    private final @Nullable UUID generationId;

    /**
     * 创建一条在线实例记录；显式目标没有固定代次时可传入 null。
     *
     * @param instanceId 实例标识
     * @param generationId 在线实例的启动代次；显式目标可为空
     */
    public JobInstance(@NotNull String instanceId, @Nullable UUID generationId) {
        this.instanceId = new InstanceId(instanceId);
        this.generationId = generationId;
    }

    /** @return 在线或显式指定的实例标识 */
    public @NotNull InstanceId instanceId() {
        return instanceId;
    }

    /** @return 在线实例的启动代次；显式目标未绑定代次时为空 */
    public @Nullable UUID generationId() {
        return generationId;
    }
}
