package com.sca4d.core.api.job;

import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.scheduling.OverlapPolicy;
import com.sca4d.core.api.scheduling.ScheduleKey;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 提交持久化 Job Run、触发无记录工作，并查询或等待已保存的结果。
 *
 * <p>实现只访问当前应用运行时命名空间内的 run。异步执行不继承提交线程的身份、Session、ThreadLocal
 * 或事务；需要在后台恢复的数据必须放入 Job 的类型化输入中。
 */
public interface JobExecutor {

    /**
     * 在系统身份与工作键的并发范围内提交一次持久化运行，不继承调用线程的身份或事务。
     *
     * @param <I> Job 输入类型
     * @param <O> Job 输出类型
     * @param job 已登记的 Job
     * @param input 与 Job 输入类型一致的数据；Void 输入可以为 null
     * @return 区分新建、复用同一输入与跳过其他输入的提交结果
     * @throws IllegalArgumentException Job 未登记、无记录、定义不一致、输入无效或目标为全部实例时抛出
     */
    @CheckReturnValue
    <I, O> @NotNull JobSubmission submit(@NotNull Job<I, O> job, @Nullable I input);

    /**
     * 保存定时 fire 对应的 run，并按计划 key 和原定时间去重。
     *
     * @param <I> Job 输入类型
     * @param <O> Job 输出类型
     * @param job 已登记的 Job
     * @param input 计划固定输入
     * @param scheduleKey 产生本次 fire 的稳定计划标识
     * @param scheduledAt Quartz 记录的原定触发时间
     * @param overlapPolicy 同一工作键已有活动运行时的处理策略
     * @return 已创建或同一 fire 已存在的 run；禁止重叠且前一次仍活动时返回空
     * @throws IllegalArgumentException Job 未登记、定义不一致、输入无效或计划参数无效时抛出
     */
    @CheckReturnValue
    <I, O> @NotNull Optional<Id> submitScheduled(
            @NotNull Job<I, O> job,
            @Nullable I input,
            @NotNull ScheduleKey scheduleKey,
            @NotNull Instant scheduledAt,
            @NotNull OverlapPolicy overlapPolicy);

    /** 根据 Job 声明的全部在线实例目标，固定目标并创建一次普通广播。 */
    <I, O> @NotNull JobDispatch broadcast(@NotNull Job<I, O> job, @Nullable I input);

    /** 使用计划 key 和原定触发时间去重，并固定本次广播的实例目标。 */
    <I, O> @NotNull JobDispatch broadcastScheduled(
            @NotNull Job<I, O> job,
            @Nullable I input,
            @NotNull ScheduleKey scheduleKey,
            @NotNull Instant scheduledAt,
            @NotNull OverlapPolicy overlapPolicy);

    /**
     * 触发一次不保存 Run 的 DirectJob；任一合格实例可直接执行，定向份额先保存短期投递。
     *
     * @param job 已登记且声明不保存 Run 的 DirectJob
     * @param input 本次计划固定输入
     * @param overlapPolicy 同一工作键已经执行时的处理方式
     * @return 已执行或已投递为 true；按跳过策略未取得执行权为 false
     */
    <I, O> boolean trigger(
            @NotNull Job<I, O> job,
            @Nullable I input,
            @NotNull OverlapPolicy overlapPolicy);

    /** 按原定触发时间投递一项不保存 Run 的定时工作。 */
    <I, O> boolean triggerScheduled(
            @NotNull Job<I, O> job,
            @Nullable I input,
            @NotNull ScheduleKey scheduleKey,
            @NotNull Instant scheduledAt,
            @NotNull OverlapPolicy overlapPolicy);

    /**
     * 提交 Job 并限时等待最终结果。
     *
     * @param <I> Job 输入类型
     * @param <O> Job 输出类型
     * @param job 已登记的 Job
     * @param input Job 输入
     * @param timeout 正数等待时长；超时不取消持久化 run
     * @return Job 输出；Void 输出返回 null
     * @throws JobWaitTimeoutException 等待时间结束但 run 尚未完成时抛出
     * @throws JobRunFailedException run 失败时抛出
     * @throws IllegalArgumentException Job、输入或等待时长无效时抛出
     * @throws IllegalStateException 同键其他输入正在执行，本次提交被跳过时抛出
     */
    @CheckReturnValue
    <I, O> @Nullable O run(
            @NotNull Job<I, O> job,
            @Nullable I input,
            @NotNull Duration timeout);

    /**
     * 返回当前应用运行时命名空间内指定 run 的状态。
     *
     * @param runId 持久化 run 标识
     * @return 当前根运行状态
     * @throws IllegalArgumentException 当前命名空间内不存在该 run 时抛出
     */
    @CheckReturnValue
    @NotNull
    JobRunStatus status(@NotNull Id runId);

    /**
     * 读取已经成功的结果，不等待仍在执行的 run。
     *
     * @param <O> Job 输出类型
     * @param runId run 标识
     * @param job 用于校验 key、定义版本和恢复输出类型的 Job
     * @return run 尚未成功时为空；Void 输出成功时也为空，应结合 status 判断
     * @throws JobRunFailedException run 已失败时抛出
     * @throws IllegalArgumentException run 不存在，或 Job key、定义版本、输入输出类型与登记定义不一致时抛出
     */
    @CheckReturnValue
    <O> @NotNull Optional<O> result(
            @NotNull Id runId,
            @NotNull Job<?, O> job);

    /**
     * 限时等待已有 run 并读取结果。
     *
     * @param <O> Job 输出类型
     * @param runId run 标识
     * @param job 用于校验定义和恢复输出的 Job
     * @param timeout 正数等待时长
     * @return Job 输出；Void 输出返回 null
     * @throws JobWaitTimeoutException 等待时间结束但 run 尚未完成时抛出；run 不会被取消
     * @throws JobRunFailedException run 失败时抛出
     * @throws IllegalArgumentException run 不存在、Job 定义不匹配或等待时长不是正数时抛出
     */
    @CheckReturnValue
    <O> @Nullable O await(
            @NotNull Id runId,
            @NotNull Job<?, O> job,
            @NotNull Duration timeout);
}
