package com.sca4d.job.persistence;

import com.sca4d.core.api.job.JobKey;
import com.sca4d.job.JobAttemptStatus;
import com.sca4d.job.JobNodeStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/** 保存一个 worker 已经持有租约的节点及其 Job 定义定位信息。 */
public class NodeClaim {

    private final long nodeId;

    private final long runId;

    private final @NotNull JobKey jobKey;

    private final int jobDefinitionVersion;

    private final @NotNull String jobKind;

    private final @NotNull String nodeKey;

    private final @Nullable String inputJson;

    private final int attempt;

    private final int maxAttempts;

    private final long retryDelayMillis;

    private @Nullable JobNodeStatus resultStatus;

    private @Nullable JobAttemptStatus attemptStatus;

    private @Nullable String outputJson;

    private @Nullable String error;

    private @Nullable Instant availableAt;

    private @Nullable Instant completedAt;

    /**
     * 创建已领取节点的完整执行列值。
     *
     * @param nodeId 节点数据库主键
     * @param runId 根运行数据库主键
     * @param jobKey 提交时保存的 Job key
     * @param jobDefinitionVersion 提交时保存的 Job 定义版本
     * @param jobKind DIRECT 或 PIPELINE
     * @param nodeKey 节点在 Job 内的稳定名称
     * @param inputJson 已准备的输入 JSON；Void 或合并节点为空
     * @param attempt 当前从 1 开始的尝试次数
     * @param maxAttempts 当前节点允许的最大尝试次数
     * @param retryDelayMillis 失败后再次可领取前的固定毫秒数
     */
    public NodeClaim(
            long nodeId,
            long runId,
            @NotNull JobKey jobKey,
            int jobDefinitionVersion,
            @NotNull String jobKind,
            @NotNull String nodeKey,
            @Nullable String inputJson,
            int attempt,
            int maxAttempts,
            long retryDelayMillis) {
        this.nodeId = nodeId;
        this.runId = runId;
        this.jobKey = jobKey;
        this.jobDefinitionVersion = jobDefinitionVersion;
        this.jobKind = jobKind;
        this.nodeKey = nodeKey;
        this.inputJson = inputJson;
        this.attempt = attempt;
        this.maxAttempts = maxAttempts;
        this.retryDelayMillis = retryDelayMillis;
    }

    /**
     * 返回已经领取的节点主键。
     *
     * @return 节点主键
     */
    public long nodeId() {
        return nodeId;
    }

    /** 保存本次尝试的成功输出，Void 输出允许为空。 */
    public @NotNull NodeClaim succeeded(@Nullable String output, @NotNull Instant at) {
        NodeClaim result = copy();
        result.resultStatus = JobNodeStatus.SUCCEEDED;
        result.attemptStatus = JobAttemptStatus.SUCCEEDED;
        result.outputJson = output;
        result.completedAt = Objects.requireNonNull(at, "尝试结束时间不能为空");
        return result;
    }

    /** 保存失败事实及业务层确定的下次可执行时间，本次尝试结束但节点继续等待。 */
    public @NotNull NodeClaim retry(@NotNull String failure, @NotNull Instant nextAttemptAt, @NotNull Instant at) {
        NodeClaim result = copy();
        result.resultStatus = JobNodeStatus.READY;
        result.attemptStatus = JobAttemptStatus.FAILED;
        result.error = Objects.requireNonNull(failure, "失败原因不能为空");
        result.availableAt = Objects.requireNonNull(nextAttemptAt, "下次尝试时间不能为空");
        result.completedAt = Objects.requireNonNull(at, "尝试结束时间不能为空");
        return result;
    }

    /** @return 处理本次尝试后节点应保存的状态 */
    public @NotNull JobNodeStatus resultStatus() {
        return Objects.requireNonNull(resultStatus, "本次尝试尚无结果");
    }

    /** @return 本次尝试应保存的结果状态 */
    public @NotNull JobAttemptStatus attemptStatus() {
        return Objects.requireNonNull(attemptStatus, "本次尝试尚无结果");
    }

    /** @return 成功输出 JSON；Void 或没有成功结果时为空 */
    public @Nullable String outputJson() {
        return outputJson;
    }

    /** @return 本次失败原因；成功时为空 */
    public @Nullable String error() {
        return error;
    }

    /** @return 下次尝试时间；不再重试时为空 */
    public @Nullable Instant availableAt() {
        return availableAt;
    }

    /** @return 本次尝试已经结束的时间 */
    public @NotNull Instant completedAt() {
        return Objects.requireNonNull(completedAt, "本次尝试尚未结束");
    }

    /** 复制本次领取的身份、尝试序号和定义信息，结果不修改原快照。 */
    private @NotNull NodeClaim copy() {
        return new NodeClaim(
                nodeId,
                runId,
                jobKey,
                jobDefinitionVersion,
                jobKind,
                nodeKey,
                inputJson,
                attempt,
                maxAttempts,
                retryDelayMillis);
    }

    /**
     * 返回当前节点所属的根运行主键。
     *
     * @return 根运行主键
     */
    public long runId() {
        return runId;
    }

    /**
     * 返回恢复代码定义使用的 Job key。
     *
     * @return Job key 文本
     */
    public @NotNull JobKey jobKey() {
        return jobKey;
    }

    /**
     * 返回提交根运行时保存的 Job 定义版本。
     *
     * @return Job 定义版本
     */
    public int jobDefinitionVersion() {
        return jobDefinitionVersion;
    }

    /**
     * 返回根运行采用的 Job 种类。
     *
     * @return DIRECT 或 PIPELINE
     */
    public @NotNull String jobKind() {
        return jobKind;
    }

    /**
     * 返回当前节点在 Job 定义内的稳定名称。
     *
     * @return 节点在 Job 内的稳定名称
     */
    public @NotNull String nodeKey() {
        return nodeKey;
    }

    /**
     * 返回节点领取前已经准备的输入 JSON。
     *
     * @return 已准备的输入 JSON；Void 或并发合并节点为空
     */
    public @Nullable String inputJson() {
        return inputJson;
    }

    /**
     * 返回当前租约对应的尝试次数。
     *
     * @return 本次从 1 开始的尝试次数
     */
    public int attempt() {
        return attempt;
    }

    /**
     * 返回当前节点允许的最大尝试次数。
     *
     * @return 当前节点最大尝试次数
     */
    public int maxAttempts() {
        return maxAttempts;
    }

    /**
     * 返回当前节点失败后的固定重试间隔。
     *
     * @return 失败后再次 READY 前的固定毫秒数
     */
    public long retryDelayMillis() {
        return retryDelayMillis;
    }
}
