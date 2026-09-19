package com.sca4d.core.api.job;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 由一个工作实例直接执行完整业务动作的 Job。
 *
 * <p>运行时按照至少一次（at-least-once）语义调用该 Job；worker 在结果写回前失联时，同一节点可能由其他
 * worker 再次执行。实现应使用业务唯一键、状态条件或其他明确方式保证重复执行不会产生错误结果。
 *
 * @param <I> 输入类型
 * @param <O> 输出类型
 */
public abstract class DirectJob<I, O> extends Job<I, O> {

    /** 供具体业务动作定义初始化 DirectJob。 */
    protected DirectJob() {
    }

    /**
     * 返回当前业务动作的重试规则。
     *
     * @return 包含首次执行的最大尝试次数和固定重试间隔
     */
    @Contract(pure = true)
    public abstract @NotNull JobRetryPolicy retryPolicy();

    /**
     * 执行一次已经领取的尝试。
     *
     * @param execution 当前 run、节点、尝试次数和开始时间
     * @param input 从持久化输入恢复的数据；输入类型为 Void 时是 null
     * @return 要持久化的结果；输出类型为 Void 时可以是 null
     * @throws RuntimeException 当前尝试失败时抛出；Job runtime 根据 {@link #retryPolicy()} 决定重试或结束 run
     */
    protected abstract @Nullable O execute(
            @NotNull JobExecution execution,
            @Nullable I input);

    /**
     * 供 Job runtime 调用当前业务动作，不绕过子类定义的执行方法。
     *
     * @param execution 当前执行事实
     * @param input 已恢复的输入
     * @return 当前尝试产生的输出
     * @throws RuntimeException {@link #execute(JobExecution, Object)} 拒绝输入或业务动作失败时抛出
     */
    public final @Nullable O run(@NotNull JobExecution execution, @Nullable I input) {
        return execute(execution, input);
    }
}
