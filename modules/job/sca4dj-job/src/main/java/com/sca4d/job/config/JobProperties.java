package com.sca4d.job.config;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

/** 配置 Job worker、租约和成功历史清理。 */
@ConfigurationProperties("sca4dj.job")
public class JobProperties {

    private int workerCount = 4;

    private int claimBatchSize = 10;

    private Duration pollInterval = Duration.ofMillis(500);

    private Duration leaseDuration = Duration.ofMinutes(1);

    private Duration leaseRenewInterval = Duration.ofSeconds(20);

    private Duration succeededRetention = Duration.ofDays(30);

    private Duration historyCleanupInterval = Duration.ofHours(1);

    private int historyCleanupBatchSize = 500;

    /** 创建使用默认 worker、轮询和租约参数的配置。 */
    public JobProperties() {
    }

    /**
     * 返回同时执行业务节点的 worker 数量。
     *
     * @return 同时执行业务节点的 worker 数量
     */
    @Contract(pure = true)
    public int getWorkerCount() {
        return workerCount;
    }

    /**
     * 修改 worker 数量；自动装配创建 worker 前由 {@link #validate()} 检查范围。
     *
     * @param workerCount 大于零的 worker 数量
     */
    @Contract(mutates = "this")
    public void setWorkerCount(int workerCount) {
        this.workerCount = workerCount;
    }

    /**
     * 返回每轮最多领取的节点数。
     *
     * @return 每轮最多领取的节点数
     */
    @Contract(pure = true)
    public int getClaimBatchSize() {
        return claimBatchSize;
    }

    /**
     * 修改单轮领取上限；自动装配创建 worker 前由 {@link #validate()} 检查范围。
     *
     * @param claimBatchSize 大于零的领取数量
     */
    @Contract(mutates = "this")
    public void setClaimBatchSize(int claimBatchSize) {
        this.claimBatchSize = claimBatchSize;
    }

    /**
     * 返回相邻两轮节点领取之间的间隔。
     *
     * @return 没有可执行节点时的轮询间隔
     */
    @Contract(pure = true)
    public @NotNull Duration getPollInterval() {
        return pollInterval;
    }

    /**
     * 修改节点轮询间隔；自动装配创建 worker 前由 {@link #validate()} 检查范围。
     *
     * @param pollInterval 大于零的轮询间隔
     */
    @Contract(mutates = "this")
    public void setPollInterval(@NotNull Duration pollInterval) {
        this.pollInterval = Objects.requireNonNull(pollInterval, "Job 轮询间隔不能为空");
    }

    /**
     * 返回一个 worker 持有节点的租约时长。
     *
     * @return worker 失联后允许其他实例接管的租约时长
     */
    @Contract(pure = true)
    public @NotNull Duration getLeaseDuration() {
        return leaseDuration;
    }

    /**
     * 修改节点租约时长；自动装配创建 worker 前由 {@link #validate()} 检查范围。
     *
     * @param leaseDuration 大于零的租约时长
     */
    @Contract(mutates = "this")
    public void setLeaseDuration(@NotNull Duration leaseDuration) {
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "Job 租约时长不能为空");
    }

    /**
     * 返回 worker 执行节点期间的续租间隔。
     *
     * @return 正在执行的节点续租间隔
     */
    @Contract(pure = true)
    public @NotNull Duration getLeaseRenewInterval() {
        return leaseRenewInterval;
    }

    /**
     * 修改节点续租间隔；自动装配创建 worker 前由 {@link #validate()} 检查范围。
     *
     * @param leaseRenewInterval 小于租约时长的正数间隔
     */
    @Contract(mutates = "this")
    public void setLeaseRenewInterval(@NotNull Duration leaseRenewInterval) {
        this.leaseRenewInterval = Objects.requireNonNull(leaseRenewInterval, "Job 续租间隔不能为空");
    }

    /**
     * 返回成功 Job run 的保留时间。
     *
     * @return 从 run 完成时间起计算的成功历史保留时间
     */
    @Contract(pure = true)
    public @NotNull Duration getSucceededRetention() {
        return succeededRetention;
    }

    /**
     * 修改成功 Job run 的保留时间。
     *
     * @param succeededRetention 大于零的成功历史保留时间
     */
    @Contract(mutates = "this")
    public void setSucceededRetention(@NotNull Duration succeededRetention) {
        this.succeededRetention = Objects.requireNonNull(succeededRetention, "Job 成功历史保留时间不能为空");
    }

    /**
     * 返回相邻两次成功历史清理之间的时间。
     *
     * @return 成功历史清理间隔
     */
    @Contract(pure = true)
    public @NotNull Duration getHistoryCleanupInterval() {
        return historyCleanupInterval;
    }

    /**
     * 修改成功历史清理间隔。
     *
     * @param historyCleanupInterval 大于零的清理间隔
     */
    @Contract(mutates = "this")
    public void setHistoryCleanupInterval(@NotNull Duration historyCleanupInterval) {
        this.historyCleanupInterval = Objects.requireNonNull(
                historyCleanupInterval,
                "Job 历史清理间隔不能为空");
    }

    /**
     * 返回单次最多删除的成功根运行数。
     *
     * @return 单次成功历史删除上限
     */
    @Contract(pure = true)
    public int getHistoryCleanupBatchSize() {
        return historyCleanupBatchSize;
    }

    /**
     * 修改单次成功历史删除上限。
     *
     * @param historyCleanupBatchSize 大于零的删除数量
     */
    @Contract(mutates = "this")
    public void setHistoryCleanupBatchSize(int historyCleanupBatchSize) {
        this.historyCleanupBatchSize = historyCleanupBatchSize;
    }

    /**
     * 校验 worker、租约和历史保留配置。
     *
     * @throws IllegalArgumentException 数量、间隔无效或续租间隔不小于租约时长时抛出
     */
    public void validate() {
        if (workerCount < 1 || claimBatchSize < 1) {
            throw new IllegalArgumentException("Job worker 数量和领取批次必须大于零");
        }
        if (pollInterval.isZero() || pollInterval.isNegative()) {
            throw new IllegalArgumentException("Job 轮询间隔必须大于零");
        }
        if (leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("Job 租约时长必须大于零");
        }
        if (leaseRenewInterval.isZero()
                || leaseRenewInterval.isNegative()
                || leaseRenewInterval.compareTo(leaseDuration) >= 0) {
            throw new IllegalArgumentException("Job 续租间隔必须大于零且小于租约时长");
        }
        if (succeededRetention.isZero() || succeededRetention.isNegative()) {
            throw new IllegalArgumentException("Job 成功历史保留时间必须大于零");
        }
        if (historyCleanupInterval.isZero() || historyCleanupInterval.isNegative()) {
            throw new IllegalArgumentException("Job 历史清理间隔必须大于零");
        }
        if (historyCleanupBatchSize < 1) {
            throw new IllegalArgumentException("Job 历史清理批量必须大于零");
        }
    }
}
