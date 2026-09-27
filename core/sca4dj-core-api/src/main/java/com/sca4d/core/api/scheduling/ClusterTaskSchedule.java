package com.sca4d.core.api.scheduling;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 使用代码声明一项由集群定时器直接执行的基础设施任务。 */
public class ClusterTaskSchedule {

    private final @NotNull ScheduleKey key;

    private final int definitionVersion;

    private final @NotNull ClusterTask task;

    private final @NotNull ScheduleTiming timing;

    private final @NotNull MisfirePolicy misfirePolicy;

    /**
     * 创建一项完整的集群任务计划。
     *
     * @param key 不依赖 Bean 名和 Quartz key 的稳定计划标识
     * @param definitionVersion 计划时间或任务定义发生变化时递增的正整数版本
     * @param task 到达触发时间后直接执行的基础设施任务
     * @param timing Cron 或固定间隔时间定义
     * @param misfirePolicy Scheduler 恢复后处理错过时间的策略
     * @throws NullPointerException key、任务、时间定义或策略为空时抛出
     * @throws IllegalArgumentException definitionVersion 不是正数时抛出
     */
    public ClusterTaskSchedule(
            @NotNull ScheduleKey key,
            int definitionVersion,
            @NotNull ClusterTask task,
            @NotNull ScheduleTiming timing,
            @NotNull MisfirePolicy misfirePolicy) {
        if (definitionVersion < 1) {
            throw new IllegalArgumentException("ClusterTaskSchedule definitionVersion 必须为正数");
        }
        this.key = Objects.requireNonNull(key, "ClusterTask ScheduleKey 不能为空");
        this.definitionVersion = definitionVersion;
        this.task = Objects.requireNonNull(task, "ClusterTask 不能为空");
        this.timing = Objects.requireNonNull(timing, "ClusterTask 计划时间不能为空");
        this.misfirePolicy = Objects.requireNonNull(misfirePolicy, "ClusterTask misfire 策略不能为空");
    }

    /** @return 稳定集群任务计划标识 */
    @Contract(pure = true)
    public @NotNull ScheduleKey key() {
        return key;
    }

    /** @return 计划定义版本 */
    @Contract(pure = true)
    public int definitionVersion() {
        return definitionVersion;
    }

    /** @return 到达触发时间后直接执行的基础设施任务 */
    @Contract(pure = true)
    public @NotNull ClusterTask task() {
        return task;
    }

    /** @return Cron 或固定间隔时间定义 */
    @Contract(pure = true)
    public @NotNull ScheduleTiming timing() {
        return timing;
    }

    /** @return Scheduler 恢复后处理错过触发时间的方式 */
    @Contract(pure = true)
    public @NotNull MisfirePolicy misfirePolicy() {
        return misfirePolicy;
    }
}
