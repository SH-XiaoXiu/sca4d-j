package com.sca4d.core.api.job;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.retry.RetryRule;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 由一个工作实例直接执行完整业务动作的 Job。
 *
 * <p>持久化运行按至少一次语义执行；worker 在结果写回前失联时，同一节点可能再次执行。无记录任务不保证失败重试。
 * 实现应使用业务唯一键、状态条件或其他明确方式处理重复执行。
 *
 * @param <I> 输入类型
 * @param <O> 输出类型
 */
public abstract class DirectJob<I, O> extends Job<I, O> {

    /** 供具体业务动作定义初始化 DirectJob。 */
    protected DirectJob() {
    }

    /** 使用已有业务动作登记一项不保存 Run 的系统工作。 */
    public static @NotNull DirectJob<Void, Void> unrecorded(
            @NotNull JobKey key,
            int definitionVersion,
            @NotNull TreeCode capability,
            @NotNull Runnable action) {
        JobKey checkedKey = Objects.requireNonNull(key, "JobKey 不能为空");
        TreeCode checkedCapability = Objects.requireNonNull(capability, "Job 能力不能为空");
        Runnable checkedAction = Objects.requireNonNull(action, "Job 动作不能为空");
        if (definitionVersion < 1) {
            throw new IllegalArgumentException("Job 定义版本必须为正数");
        }
        return new DirectJob<>() {
            @Override
            public @NotNull JobKey key() {
                return checkedKey;
            }

            @Override
            public int definitionVersion() {
                return definitionVersion;
            }

            @Override
            public @NotNull Class<Void> inputType() {
                return Void.class;
            }

            @Override
            public @NotNull Class<Void> outputType() {
                return Void.class;
            }

            @Override
            public @NotNull TreeCode requiredCapability() {
                return checkedCapability;
            }

            @Override
            public @NotNull JobRunRecording runRecording() {
                return JobRunRecording.NONE;
            }

            @Override
            public @NotNull RetryRule retryRule() {
                return RetryRule.once();
            }

            @Override
            protected @Nullable Void execute(@NotNull JobExecution execution, @Nullable Void input) {
                checkedAction.run();
                return null;
            }
        };
    }

    /**
     * 返回当前业务动作的重试规则。
     *
     * @return 包含首次执行的次数限制和退避规则
     */
    @Contract(pure = true)
    public abstract @NotNull RetryRule retryRule();

    /**
     * 执行一次已经领取的尝试。
     *
     * @param execution 当前执行标识、可选 run、尝试次数和开始时间
     * @param input 本次工作输入；输入类型为 Void 时是 null
     * @return 本次执行结果；输出类型为 Void 时可以是 null
     * @throws RuntimeException 当前尝试失败时抛出；持久化 Job 按 {@link #retryRule()} 决定后续状态
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
