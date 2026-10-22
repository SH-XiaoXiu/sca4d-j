package com.sca4d.core.api.job;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.scheduling.OverlapPolicy;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * 表示可独立命名、由具备所需能力的实例执行的一项工作定义。
 *
 * <p>Job 声明稳定标识、定义版本、输入输出类型、系统执行能力和并发工作键。持久化 Job 由 runtime 保存运行与结果；
 * 无记录 Job 直接执行或短期投递。定时调度只在触发时间发起工作，不改变 Job 的执行语义。
 *
 * @param <I> 输入类型
 * @param <O> 输出类型
 */
public abstract class Job<I, O> {

    /** 供具体 Job 定义初始化稳定元数据。 */
    protected Job() {
    }

    /**
     * 返回当前工作定义的稳定标识。
     *
     * @return 不依赖 Bean 名和 Java 类名的 Job 标识
     */
    @Contract(pure = true)
    public abstract @NotNull JobKey key();

    /**
     * 返回当前工作定义版本。
     *
     * @return 定义或执行语义发生不兼容变化时递增的正整数版本
     */
    @Contract(pure = true)
    public abstract int definitionVersion();

    /**
     * 返回持久化输入类型。
     *
     * @return 序列化和恢复输入使用的具体 Java 类型
     */
    @Contract(pure = true)
    public abstract @NotNull Class<I> inputType();

    /**
     * 返回持久化输出类型。
     *
     * @return 序列化和恢复成功结果使用的具体 Java 类型
     */
    @Contract(pure = true)
    public abstract @NotNull Class<O> outputType();

    /**
     * 返回执行该 Job 所需的实例能力；能力定义同时选择后台使用的系统身份。
     *
     * @return 当前 Job 所需的能力编码
     */
    @Contract(pure = true)
    public @NotNull TreeCode requiredCapability() {
        return TreeCode.ofColon("JOB:WORKER");
    }

    /**
     * 返回是否保存独立运行记录。
     *
     * @return 持久化运行记录策略
     */
    @Contract(pure = true)
    public @NotNull JobRunRecording runRecording() {
        return JobRunRecording.PERSISTED;
    }

    /**
     * 根据本次输入返回并发控制使用的工作键。
     *
     * @param input 本次 Job 输入；Void Job 为 null，其他输入非空
     * @return 同一系统执行身份下的工作键
     */
    @Contract(pure = true)
    public @NotNull JobWorkKey workKey(I input) {
        return JobWorkKey.fixed(key());
    }

    /**
     * 根据本次输入确定工作目标。
     *
     * @param input 本次输入；Void Job 为 null，其他输入非空
     * @return 默认由任一合格实例执行一次
     */
    @Contract(pure = true)
    public @NotNull JobTarget target(I input) {
        return JobTarget.any();
    }

    /**
     * 返回同一工作键已有活动运行时的新提交处理方式。
     *
     * @return 默认跳过活动工作
     */
    @Contract(pure = true)
    public @NotNull OverlapPolicy overlapPolicy() {
        return OverlapPolicy.SKIP_IF_RUNNING;
    }
}
