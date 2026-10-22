package com.sca4d.job.persistence;

import com.sca4d.core.api.retry.RetryRule;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 保存已经锁定的过期 Job 尝试，用原租约截止时间计算恢复时间。 */
public class ExpiredJobAttempt {
    private final Instant leaseUntil;

    private final RetryRule retryRule;

    private final String workerId;

    private final long attempt;

    private final long nodeId;

    /**
     * @param nodeId 节点标识
     * @param attempt 已开始次数
     * @param workerId 旧租约持有者
     * @param leaseUntil 原截止时间
     * @param retryRule 冻结规则
     */
    public ExpiredJobAttempt(
            long nodeId,
            long attempt,
            @NotNull String workerId,
            @NotNull Instant leaseUntil,
            @NotNull RetryRule retryRule) {
        this.nodeId = nodeId;
        this.attempt = attempt;
        this.workerId = Objects.requireNonNull(workerId);
        this.leaseUntil = Objects.requireNonNull(leaseUntil);
        this.retryRule = Objects.requireNonNull(retryRule);
    }

    /** @return 过期尝试对应的节点标识 */
    public long nodeId() {
        return nodeId;
    }

    /** @return 已经开始的尝试次数 */
    public long attempt() {
        return attempt;
    }

    /** @return 原租约持有者 */
    public @NotNull String workerId() {
        return workerId;
    }

    /** @return 原租约截止时间，恢复退避的计算起点 */
    public @NotNull Instant leaseUntil() {
        return leaseUntil;
    }

    /** @return 节点创建时冻结的规则 */
    public @NotNull RetryRule retryRule() {
        return retryRule;
    }
}
