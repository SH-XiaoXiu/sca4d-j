package com.sca4d.config.properties;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Quartz 集群名称、线程数量、故障判定时间和启动锁等待时间。 */
@ConfigurationProperties(prefix = "sca4dj.scheduler")
public class Sca4djSchedulerProperties {

    private String schedulerName;
    private int threadCount;
    private Duration clusterCheckinInterval;
    private Duration misfireThreshold;
    private Duration shutdownWait;
    private Duration bootstrapLockTimeout;
    private Duration bootstrapLockRetryInterval;

    /** @return 集群中全部 Server 节点共用的 Scheduler 名称 */
    public @NotNull String getSchedulerName() {
        return schedulerName;
    }

    /** @param schedulerName 集群中全部 Server 节点共用的非空 Scheduler 名称 */
    public void setSchedulerName(@NotNull String schedulerName) {
        String value = schedulerName.trim();
        if (value.isEmpty() || value.length() > 120) {
            throw new IllegalArgumentException("Scheduler 名称长度必须为 1 至 120");
        }
        this.schedulerName = value;
    }

    /** @return 当前节点 Quartz 工作线程数 */
    public int getThreadCount() {
        return threadCount;
    }

    /** @param threadCount 当前节点 Quartz 工作线程数，范围为 1 至 200 */
    public void setThreadCount(int threadCount) {
        if (threadCount < 1 || threadCount > 200) {
            throw new IllegalArgumentException("Quartz 工作线程数必须为 1 至 200");
        }
        this.threadCount = threadCount;
    }

    /** @return Quartz 集群节点签到间隔 */
    public @NotNull Duration getClusterCheckinInterval() {
        return clusterCheckinInterval;
    }

    /** @param value Quartz 集群节点签到间隔 */
    public void setClusterCheckinInterval(@NotNull Duration value) {
        this.clusterCheckinInterval = requireDuration(value, Duration.ofSeconds(1), Duration.ofMinutes(5), "集群签到间隔");
    }

    /** @return Quartz misfire 判定阈值 */
    public @NotNull Duration getMisfireThreshold() {
        return misfireThreshold;
    }

    /** @param value Quartz misfire 判定阈值 */
    public void setMisfireThreshold(@NotNull Duration value) {
        this.misfireThreshold = requireDuration(value, Duration.ofSeconds(1), Duration.ofHours(24), "misfire 阈值");
    }

    /** @return 应用关闭时等待运行中 Job 完成的最长时间 */
    public @NotNull Duration getShutdownWait() {
        return shutdownWait;
    }

    /** @param value 应用关闭时等待运行中 Job 完成的最长时间 */
    public void setShutdownWait(@NotNull Duration value) {
        this.shutdownWait = requireDuration(value, Duration.ofSeconds(1), Duration.ofMinutes(30), "关闭等待时间");
    }

    /** @return 获取 PostgreSQL 启动锁的总等待上限 */
    public @NotNull Duration getBootstrapLockTimeout() {
        return bootstrapLockTimeout;
    }

    /** @param value 获取 PostgreSQL 启动锁的总等待上限 */
    public void setBootstrapLockTimeout(@NotNull Duration value) {
        this.bootstrapLockTimeout = requireDuration(value, Duration.ofSeconds(1), Duration.ofMinutes(10), "启动锁超时");
    }

    /** @return 未取得启动锁时两次尝试之间的等待间隔 */
    public @NotNull Duration getBootstrapLockRetryInterval() {
        return bootstrapLockRetryInterval;
    }

    /** @param value 未取得启动锁时两次尝试之间的等待间隔 */
    public void setBootstrapLockRetryInterval(@NotNull Duration value) {
        this.bootstrapLockRetryInterval = requireDuration(value, Duration.ofMillis(25), Duration.ofSeconds(10), "启动锁重试间隔");
    }

    /**
     * 校验调度时长必须为正数且不能超过允许的最大值。
     *
     * @param value 配置绑定得到的 Duration
     * @param minimum 当前配置允许的最小值
     * @param maximum 当前配置允许的最大值
     * @param name 配置错误中使用的安全名称
     * @return 已通过正数和最大值校验的原 Duration
     * @throws IllegalArgumentException 配置超出闭区间时抛出
     */
    private static @NotNull Duration requireDuration(
            @NotNull Duration value, @NotNull Duration minimum, @NotNull Duration maximum, @NotNull String name) {
        if (value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(name + "必须在 " + minimum + " 至 " + maximum + " 之间");
        }
        return value;
    }
}
