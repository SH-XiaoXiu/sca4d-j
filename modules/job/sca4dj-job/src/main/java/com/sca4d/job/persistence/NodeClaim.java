package com.sca4d.job.persistence;

import com.sca4d.core.api.job.JobKey;
import com.sca4d.core.api.retry.RetryRule;
import com.sca4d.job.JobAttemptStatus;
import com.sca4d.job.JobKind;
import com.sca4d.job.JobNodeStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/** 保存一个 worker 已经持有租约的节点及其 Job 定义定位信息。 */
public class NodeClaim {
    private final @NotNull JobKey jobKey;

    private final @NotNull JobKind jobKind;

    private final @NotNull RetryRule retryRule;

    private final @Nullable String inputJson;

    private final @NotNull String nodeKey;

    private final int jobDefinitionVersion;

    private final long attempt;

    private final long nodeId;

    private final long runId;

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
     * @param jobKind 根运行的 Job 类型，DIRECT 或 PIPELINE
     * @param nodeKey 节点在 Job 内的名称
     * @param inputJson 已准备的输入 JSON；Void 或合并节点为空
     * @param attempt 当前从 1 开始的尝试次数
     * @param retryRule 节点创建时冻结的重试规则
     */
    public NodeClaim(
            long nodeId,
            long runId,
            @NotNull JobKey jobKey,
            int jobDefinitionVersion,
            @NotNull JobKind jobKind,
            @NotNull String nodeKey,
            @Nullable String inputJson,
            long attempt,
            @NotNull RetryRule retryRule) {
        this.nodeId = nodeId;
        this.runId = runId;
        this.jobKey = jobKey;
        this.jobDefinitionVersion = jobDefinitionVersion;
        this.jobKind = jobKind;
        this.nodeKey = nodeKey;
        this.inputJson = inputJson;
        this.attempt = attempt;
        this.retryRule = Objects.requireNonNull(retryRule);
    }

    /**
     * 已经领取的节点主键。
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

    /** 记录本次尝试失败，并保存下次可执行时间；本次尝试结束，节点回到等待状态。 */
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
                retryRule);
    }

    /**
     * 当前节点所属的根运行主键。
     *
     * @return 根运行主键
     */
    public long runId() {
        return runId;
    }

    /**
     * 恢复 Job 定义使用的 Job key。
     *
     * @return 提交根运行时保存的 JobKey 对象
     */
    public @NotNull JobKey jobKey() {
        return jobKey;
    }

    /**
     * 提交根运行时保存的 Job 定义版本。
     *
     * @return Job 定义版本
     */
    public int jobDefinitionVersion() {
        return jobDefinitionVersion;
    }

    /**
     * 根运行采用的 Job 种类。
     *
     * @return DIRECT 或 PIPELINE
     */
    public @NotNull JobKind jobKind() {
        return jobKind;
    }

    /**
     * 当前节点在 Job 定义内的名称。
     *
     * @return 节点在 Job 内的名称
     */
    public @NotNull String nodeKey() {
        return nodeKey;
    }

    /**
     * 节点领取前已经准备的输入 JSON。
     *
     * @return 已准备的输入 JSON；Void 或并发合并节点为空
     */
    public @Nullable String inputJson() {
        return inputJson;
    }

    /**
     * 当前租约对应的尝试次数。
     *
     * @return 本次从 1 开始的尝试次数
     */
    public long attempt() {
        return attempt;
    }

    /** @return 本次运行创建时冻结的重试规则 */
    public @NotNull RetryRule retryRule() {
        return retryRule;
    }
}
