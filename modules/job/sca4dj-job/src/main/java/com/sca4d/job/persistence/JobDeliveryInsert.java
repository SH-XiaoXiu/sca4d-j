package com.sca4d.job.persistence;

import com.sca4d.core.api.job.JobKey;
import com.sca4d.core.api.job.JobWorkKey;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.scheduling.OverlapPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/** 批量插入无记录份额时单个目标实例的全部列值。 */
public final class JobDeliveryInsert {
    private final @NotNull OverlapPolicy overlapPolicy;

    private final @NotNull Instant expiresAt;

    private final @NotNull JobKey jobKey;

    private final @NotNull JobWorkKey workKey;

    private final @Nullable String inputJson;

    private final @NotNull String targetInstanceId;

    private final @NotNull TreeCode capability;

    private final @NotNull TreeCode systemIdentity;

    private final @Nullable UUID targetGenerationId;

    private final int definitionVersion;

    private final long dispatchId;

    /** 创建一个待批量插入的无记录份额。 */
    public JobDeliveryInsert(
            long dispatchId,
            @NotNull JobKey jobKey,
            int definitionVersion,
            @NotNull TreeCode capability,
            @NotNull TreeCode systemIdentity,
            @NotNull JobWorkKey workKey,
            @NotNull OverlapPolicy overlapPolicy,
            @Nullable String inputJson,
            @NotNull String targetInstanceId,
            @Nullable UUID targetGenerationId,
            @NotNull Instant expiresAt) {
        this.dispatchId = dispatchId;
        this.jobKey = jobKey;
        this.definitionVersion = definitionVersion;
        this.capability = capability;
        this.systemIdentity = systemIdentity;
        this.workKey = workKey;
        this.overlapPolicy = overlapPolicy;
        this.inputJson = inputJson;
        this.targetInstanceId = targetInstanceId;
        this.targetGenerationId = targetGenerationId;
        this.expiresAt = expiresAt;
    }

    /** @return 广播标识 */
    public long getDispatchId() {
        return dispatchId;
    }

    /** @return Job 键 */
    public @NotNull JobKey getJobKey() {
        return jobKey;
    }

    /** @return Job 定义版本 */
    public int getDefinitionVersion() {
        return definitionVersion;
    }

    /** @return 所需 capability */
    public @NotNull TreeCode getCapability() {
        return capability;
    }

    /** @return 系统身份 */
    public @NotNull TreeCode getSystemIdentity() {
        return systemIdentity;
    }

    /** @return 工作键 */
    public @NotNull JobWorkKey getWorkKey() {
        return workKey;
    }

    /** @return 重叠策略 */
    public @NotNull OverlapPolicy getOverlapPolicy() {
        return overlapPolicy;
    }

    /** @return 输入 JSON */
    public @Nullable String getInputJson() {
        return inputJson;
    }

    /** @return 目标实例标识 */
    public @NotNull String getTargetInstanceId() {
        return targetInstanceId;
    }

    /** @return 目标实例代次 */
    public @Nullable UUID getTargetGenerationId() {
        return targetGenerationId;
    }

    /** @return 份额过期时间 */
    public @NotNull Instant getExpiresAt() {
        return expiresAt;
    }
}
