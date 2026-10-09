package com.sca4d.core.api.scheduling;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Objects;

/** 按固定时间间隔触发 Job，不根据上一次 Job 完成时间重新计时。 */
public final class IntervalSchedule implements ScheduleTiming {

    private final @NotNull Duration interval;

    private final @NotNull Duration initialDelay;

    /**
     * 创建固定间隔计划。
     *
     * @param interval 大于零的触发间隔
     * @param initialDelay 应用完成计划登记后的非负首次延迟
     * @throws NullPointerException 触发间隔或首次延迟为空时抛出
     * @throws IllegalArgumentException 触发间隔不是正数或首次延迟为负数时抛出
     */
    public IntervalSchedule(@NotNull Duration interval, @NotNull Duration initialDelay) {
        Duration checkedInterval = Objects.requireNonNull(interval, "计划间隔不能为空");
        Duration checkedDelay = Objects.requireNonNull(initialDelay, "计划首次延迟不能为空");
        if (checkedInterval.isZero() || checkedInterval.isNegative()) {
            throw new IllegalArgumentException("计划间隔必须大于零");
        }
        if (checkedDelay.isNegative()) {
            throw new IllegalArgumentException("计划首次延迟不能为负数");
        }
        this.interval = checkedInterval;
        this.initialDelay = checkedDelay;
    }

    /**
     * 固定触发间隔。
     *
     * @return 两次原定触发之间的正数间隔
     */
    @Contract(pure = true)
    public @NotNull Duration interval() {
        return interval;
    }

    /**
     * 首次触发延迟。
     *
     * @return 完成计划登记后的非负延迟
     */
    @Contract(pure = true)
    public @NotNull Duration initialDelay() {
        return initialDelay;
    }
}
