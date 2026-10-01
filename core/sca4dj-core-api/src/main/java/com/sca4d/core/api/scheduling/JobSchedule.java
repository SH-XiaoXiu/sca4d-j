package com.sca4d.core.api.scheduling;

import com.sca4d.core.api.job.Job;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 使用代码声明何时提交一个 Job 及其固定输入。
 *
 * <p>计划只描述触发时间和提交参数，不执行 Job。Scheduler 到达原定时间后调用 JobExecutor；Job 的持久化、
 * 租约、重试和结果仍由 Job runtime 负责。
 *
 * @param <I> Job 输入类型
 * @param <O> Job 输出类型
 */
public class JobSchedule<I, O> {

    private final @NotNull MisfirePolicy misfirePolicy;

    private final @NotNull OverlapPolicy overlapPolicy;

    private final @Nullable I input;

    private final @NotNull Job<I, O> job;

    private final @NotNull ScheduleKey key;

    private final @NotNull ScheduleTiming timing;

    private final int definitionVersion;

    /**
     * 创建一项完整代码计划。
     *
     * @param key 不依赖 Bean 名和 Quartz key 的稳定计划标识
     * @param definitionVersion 计划时间、输入或策略发生变化时递增的正整数版本
     * @param job 到达触发时间后提交的已登记 Job
     * @param input 每次 fire 使用的固定输入；Void 输入可以为空
     * @param timing Cron 或固定间隔时间定义
     * @param misfirePolicy Scheduler 恢复后处理错过时间的策略
     * @param overlapPolicy 前一次 run 未结束时处理新 fire 的策略
     * @throws NullPointerException key、Job、时间定义或策略为空时抛出
     * @throws IllegalArgumentException definitionVersion 不是正数，或输入为空、类型与 Job 声明不一致时抛出
     */
    public JobSchedule(
            @NotNull ScheduleKey key,
            int definitionVersion,
            @NotNull Job<I, O> job,
            @Nullable I input,
            @NotNull ScheduleTiming timing,
            @NotNull MisfirePolicy misfirePolicy,
            @NotNull OverlapPolicy overlapPolicy) {
        if (definitionVersion < 1) {
            throw new IllegalArgumentException("JobSchedule definitionVersion 必须为正数");
        }
        this.key = Objects.requireNonNull(key, "ScheduleKey 不能为空");
        this.definitionVersion = definitionVersion;
        this.job = Objects.requireNonNull(job, "计划 Job 不能为空");
        if (input == null && !Void.class.equals(job.inputType())) {
            throw new IllegalArgumentException("非 Void Job 的计划输入不能为空: " + job.key());
        }
        if (input != null && !job.inputType().isInstance(input)) {
            throw new IllegalArgumentException("计划输入类型与 Job 不一致: " + job.key());
        }
        this.input = input;
        this.timing = Objects.requireNonNull(timing, "计划时间不能为空");
        this.misfirePolicy = Objects.requireNonNull(misfirePolicy, "misfire 策略不能为空");
        this.overlapPolicy = Objects.requireNonNull(overlapPolicy, "重叠策略不能为空");
    }

    /**
     * 稳定计划标识。
     *
     * @return 不依赖 Bean 名和 Scheduler key 的标识
     */
    @Contract(pure = true)
    public @NotNull ScheduleKey key() {
        return key;
    }

    /**
     * 计划定义版本。
     *
     * @return 计划定义发生变化时递增的正整数
     */
    @Contract(pure = true)
    public int definitionVersion() {
        return definitionVersion;
    }

    /**
     * 计划提交的 Job。
     *
     * @return 到达原定时间后交给 JobExecutor 的定义
     */
    @Contract(pure = true)
    public @NotNull Job<I, O> job() {
        return job;
    }

    /**
     * 计划固定输入。
     *
     * @return 每次触发使用的输入；Void Job 返回 null
     */
    @Contract(pure = true)
    public @Nullable I input() {
        return input;
    }

    /**
     * 触发时间定义。
     *
     * @return Cron 或固定间隔定义
     */
    @Contract(pure = true)
    public @NotNull ScheduleTiming timing() {
        return timing;
    }

    /**
     * misfire 策略。
     *
     * @return Scheduler 错过原定时间后的处理方式
     */
    @Contract(pure = true)
    public @NotNull MisfirePolicy misfirePolicy() {
        return misfirePolicy;
    }

    /**
     * 重叠策略。
     *
     * @return 前一次计划 run 未结束时的处理方式
     */
    @Contract(pure = true)
    public @NotNull OverlapPolicy overlapPolicy() {
        return overlapPolicy;
    }
}
