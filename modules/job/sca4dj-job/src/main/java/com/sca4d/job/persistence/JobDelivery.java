package com.sca4d.job.persistence;

import com.sca4d.core.api.job.JobKey;
import com.sca4d.core.api.runtime.InstanceId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 已由目标实例领取的不保留 Run 的工作份额。 */
public class JobDelivery {

    private final @NotNull InstanceId targetInstanceId;

    private final @NotNull JobKey jobKey;

    private final @Nullable String inputJson;

    private final int jobDefinitionVersion;

    private final long id;

    /**
     * 创建一份已领取投递的数据库快照。
     *
     * @param id 投递标识
     * @param jobKey 投递时登记的 Job 标识
     * @param jobDefinitionVersion 投递时登记的定义版本
     * @param inputJson 可为空的输入 JSON
     * @param targetInstanceId 领取投递的实例标识
     */
    public JobDelivery(
            long id,
            @NotNull JobKey jobKey,
            int jobDefinitionVersion,
            @Nullable String inputJson,
            @NotNull String targetInstanceId) {
        this.id = id;
        this.jobKey = jobKey;
        this.jobDefinitionVersion = jobDefinitionVersion;
        this.inputJson = inputJson;
        this.targetInstanceId = new InstanceId(targetInstanceId);
    }

    /** @return 投递标识 */
    public long id() {
        return id;
    }

    /** @return 投递时登记的 Job 标识 */
    public @NotNull JobKey jobKey() {
        return jobKey;
    }

    /** @return 投递时登记的 Job 定义版本 */
    public int jobDefinitionVersion() {
        return jobDefinitionVersion;
    }

    /** @return 可为空的输入 JSON */
    public @Nullable String inputJson() {
        return inputJson;
    }

    /** @return 领取投递的实例标识 */
    public @NotNull InstanceId targetInstanceId() {
        return targetInstanceId;
    }
}
