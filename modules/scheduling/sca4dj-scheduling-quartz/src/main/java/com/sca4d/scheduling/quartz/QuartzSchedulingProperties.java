package com.sca4d.scheduling.quartz;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

/** 配置 Quartz 工作线程数和集群节点写入数据库状态的间隔。 */
@ConfigurationProperties("sca4dj.scheduling.quartz")
public class QuartzSchedulingProperties {

    private int threadCount = 4;

    private Duration clusterCheckinInterval = Duration.ofSeconds(15);

    /** 创建使用默认线程数和集群签到间隔的配置。 */
    public QuartzSchedulingProperties() {
    }

    /**
     * 返回 Quartz 执行 trigger 的工作线程数。
     *
     * @return Quartz 获取 trigger 的线程数
     */
    @Contract(pure = true)
    public int getThreadCount() {
        return threadCount;
    }

    /**
     * 修改 Quartz 工作线程数；自动装配创建 Scheduler 前由 {@link #validate()} 检查范围。
     *
     * @param threadCount 大于零的 Quartz 线程数
     */
    @Contract(mutates = "this")
    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    /**
     * 返回 Quartz 集群实例更新存活状态的间隔。
     *
     * @return Quartz 集群实例写入数据库状态的间隔
     */
    @Contract(pure = true)
    public @NotNull Duration getClusterCheckinInterval() {
        return clusterCheckinInterval;
    }

    /**
     * 修改集群签到间隔；自动装配创建 Scheduler 前由 {@link #validate()} 检查范围。
     *
     * @param clusterCheckinInterval 大于零的节点签到间隔
     */
    @Contract(mutates = "this")
    public void setClusterCheckinInterval(@NotNull Duration clusterCheckinInterval) {
        this.clusterCheckinInterval = Objects.requireNonNull(
                clusterCheckinInterval,
                "Quartz 集群签到间隔不能为空");
    }

    /**
     * 校验 Quartz 线程和集群签到配置。
     *
     * @throws IllegalArgumentException 线程数或签到间隔无效时抛出
     */
    public void validate() {
        if (threadCount < 1) {
            throw new IllegalArgumentException("sca4dj.scheduling.quartz.thread-count 必须大于零");
        }
        if (clusterCheckinInterval.isZero()
                || clusterCheckinInterval.isNegative()) {
            throw new IllegalArgumentException(
                    "sca4dj.scheduling.quartz.cluster-checkin-interval 必须大于零");
        }
    }
}
