package com.sca4d.core.api.job;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * 表示可独立命名、持久化并由集群 worker 执行的一次工作定义。
 *
 * <p>Job 只声明稳定标识、定义版本和输入输出类型。提交、租约、重试和结果保存由 Job runtime 负责；
 * 定时调度只在到达触发时间后提交 Job，不改变 Job 的执行语义。
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
}
