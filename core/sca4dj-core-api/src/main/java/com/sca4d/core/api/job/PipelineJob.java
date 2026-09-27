package com.sca4d.core.api.job;

import com.sca4d.core.api.pipeline.Pipeline;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 把多条 Pipeline 编排为顺序、二路并发和合并节点的 Job。
 *
 * <p>Pipeline 本身仍可通过 {@link Pipeline#run(Object)} 直接同步执行。
 * PipelineJob 只负责把多条 Pipeline 声明为可恢复的节点；每个节点独立保存状态并按自身策略重试，
 * Pipeline 内部不保存阶段进度，节点重试时会从该 Pipeline 的第一个阶段重新开始。
 *
 * @param <I> 根输入类型
 * @param <O> 最终输出类型
 */
public abstract class PipelineJob<I, O> extends Job<I, O> {

    /** 供具体 Pipeline 编排定义初始化 PipelineJob。 */
    protected PipelineJob() {
    }

    /**
     * 声明当前定义版本使用的 Pipeline 编排。
     *
     * @return 与当前定义版本一致的不可变 Pipeline 编排
     */
    @Contract(pure = true)
    protected abstract @NotNull PipelineJobPlan<I, O> plan();

    /**
     * 返回供 Job runtime 展开并持久化的 Pipeline 编排。
     *
     * @return 至少包含一个 Pipeline 节点的编排
     * @throws IllegalStateException {@link #plan()} 返回空计划时抛出
     */
    @CheckReturnValue
    @Contract(pure = true)
    public final @NotNull PipelineJobPlan<I, O> definition() {
        PipelineJobPlan<I, O> definition = plan();
        if (definition.nodes().isEmpty()) {
            throw new IllegalStateException("PipelineJob 至少需要一个 Pipeline 节点: " + key());
        }
        return definition;
    }

    /**
     * 声明一个可以从持久化历史中读取的类型化节点输出。
     *
     * @param nodeKey 节点在当前 Job 定义中保存的名称
     * @param outputType 调用方准备读取的节点输出类型
     * @param <N> 节点输出类型
     * @return 同时绑定当前 Job key、定义版本、节点名称和输出类型的声明
     * @throws IllegalArgumentException 节点不存在或声明类型与节点 Pipeline 输出类型不一致时抛出
     */
    @CheckReturnValue
    @Contract(pure = true)
    public final <N> @NotNull JobNodeOutput<N> nodeOutput(
            @NotNull String nodeKey,
            @NotNull Class<N> outputType) {
        String checkedKey = Objects.requireNonNull(nodeKey, "PipelineJob 节点名称不能为空");
        Class<N> checkedType = Objects.requireNonNull(outputType, "PipelineJob 节点输出类型不能为空");
        PipelineJobPlan.Node node = definition().nodes()
                .stream()
                .filter(candidate -> candidate.key().equals(checkedKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("PipelineJob 节点不存在: " + checkedKey));
        Class<?> actualType = node.pipeline().outputType();
        if (!actualType.equals(checkedType)) {
            throw new IllegalArgumentException(
                    "PipelineJob 节点输出类型不一致: node=" + checkedKey
                            + ", expected=" + actualType.getName()
                            + ", actual=" + checkedType.getName());
        }
        return new JobNodeOutput<>(key(), definitionVersion(), checkedKey, checkedType);
    }

    @Override
    @Contract(pure = true)
    public final @NotNull Class<I> inputType() {
        return definition().inputType();
    }

    @Override
    @Contract(pure = true)
    public final @NotNull Class<O> outputType() {
        return definition().outputType();
    }
}
