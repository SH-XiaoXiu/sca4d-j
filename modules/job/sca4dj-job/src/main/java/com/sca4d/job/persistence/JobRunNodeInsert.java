package com.sca4d.job.persistence;

import com.sca4d.core.api.retry.RetryRule;
import com.sca4d.job.JobKind;
import com.sca4d.job.JobNodeStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/** 批量插入运行节点时单个节点的全部列值。 */
public final class JobRunNodeInsert {
    private final @NotNull Instant availableAt;

    private final @NotNull JobKind kind;

    private final @NotNull JobNodeStatus status;

    private final @NotNull RetryRule retryRule;

    private final @Nullable String inputJson;

    private final @NotNull String nodeKey;

    private final boolean parallelMerge;

    private final long runId;

    /** 创建一个待批量插入的运行节点。 */
    public JobRunNodeInsert(
            long runId,
            @NotNull String nodeKey,
            @NotNull JobKind kind,
            @Nullable String inputJson,
            @NotNull JobNodeStatus status,
            boolean parallelMerge,
            @NotNull RetryRule retryRule,
            @NotNull Instant availableAt) {
        this.runId = runId;
        this.nodeKey = nodeKey;
        this.kind = kind;
        this.inputJson = inputJson;
        this.status = status;
        this.parallelMerge = parallelMerge;
        this.retryRule = retryRule;
        this.availableAt = availableAt;
    }

    /** @return 所属根运行标识 */
    public long getRunId() {
        return runId;
    }

    /** @return 节点在运行内的键 */
    public @NotNull String getNodeKey() {
        return nodeKey;
    }

    /** @return 节点类型 */
    public @NotNull JobKind getKind() {
        return kind;
    }

    /** @return 节点输入 JSON；有依赖的节点为空 */
    public @Nullable String getInputJson() {
        return inputJson;
    }

    /** @return 节点初始状态 */
    public @NotNull JobNodeStatus getStatus() {
        return status;
    }

    /** @return 是否并行合并 */
    public boolean getParallelMerge() {
        return parallelMerge;
    }

    /** @return 重试规则 */
    public @NotNull RetryRule getRetryRule() {
        return retryRule;
    }

    /** @return 最早可领取时间 */
    public @NotNull Instant getAvailableAt() {
        return availableAt;
    }
}
