package com.sca4d.core.api.job;

import com.sca4d.core.api.pipeline.Pipeline;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 保存 PipelineJob 中已经类型校验的顺序、二路并发和合并关系。
 *
 * <p>构建方法始终返回新计划，不修改已有计划。每个节点包含一条可直接执行的 Pipeline；运行时只在节点边界
 * 保存输入、输出、依赖和重试状态，不保存 Pipeline 内部阶段的进度。
 *
 * @param <I> 根输入类型
 * @param <O> 当前终点输出类型
 */
public final class PipelineJobPlan<I, O> {

    private final @NotNull Class<I> inputType;

    private final @NotNull Class<O> outputType;

    private final @NotNull List<@NotNull Node> nodes;

    private final @NotNull Set<@NotNull String> nodeKeys;

    private final @Nullable String endpointKey;

    private PipelineJobPlan(
            @NotNull Class<I> inputType,
            @NotNull Class<O> outputType,
            @NotNull List<@NotNull Node> nodes,
            @NotNull Set<@NotNull String> nodeKeys,
            @Nullable String endpointKey) {
        this.inputType = inputType;
        this.outputType = outputType;
        this.nodes = List.copyOf(nodes);
        this.nodeKeys = Set.copyOf(nodeKeys);
        this.endpointKey = endpointKey;
    }

    /**
     * 从 PipelineJob 根输入开始声明计划，允许第一个动作直接形成并发分支。
     *
     * @param <I> 根输入类型
     * @param inputType 根输入的具体类型
     * @return 尚未包含 Pipeline 节点的计划
     * @throws NullPointerException 输入类型为空时抛出
     */
    @CheckReturnValue
    @Contract(pure = true)
    public static <I> @NotNull PipelineJobPlan<I, I> from(@NotNull Class<I> inputType) {
        Class<I> checked = Objects.requireNonNull(inputType, "PipelineJob 输入类型不能为空");
        return new PipelineJobPlan<>(checked, checked, List.of(), Set.of(), null);
    }

    /**
     * 在当前终点后增加一条 Pipeline。
     *
     * @param <N> 新终点输出类型
     * @param nodeKey 节点在当前 Job 内的稳定名称
     * @param pipeline 输入类型与当前输出相同的 Pipeline
     * @param retryPolicy 当前 Pipeline 节点的重试策略
     * @return 采用 Pipeline 输出作为新终点的计划
     * @throws NullPointerException 节点名称、Pipeline 或重试策略为空时抛出
     * @throws IllegalArgumentException 节点名称无效或重复，或 Pipeline 输入类型不能承接当前输出时抛出
     */
    @CheckReturnValue
    @Contract(pure = true)
    public <N> @NotNull PipelineJobPlan<I, N> then(
            @NotNull String nodeKey,
            @NotNull Pipeline<O, N> pipeline,
            @NotNull JobRetryPolicy retryPolicy) {
        Pipeline<O, N> checkedPipeline = Objects.requireNonNull(pipeline, "PipelineJob 节点不能为空");
        requireType(outputType, checkedPipeline.inputType(), "PipelineJob 顺序节点输入类型不一致");
        String checkedKey = requireNewKey(nodeKey);
        List<Node> nextNodes = new ArrayList<>(nodes);
        nextNodes.add(
                new Node(
                        checkedKey,
                        checkedPipeline,
                        Objects.requireNonNull(retryPolicy, "PipelineJob 重试策略不能为空"),
                        endpointKey == null ? List.of() : List.of(endpointKey),
                        false));
        Set<String> nextKeys = new HashSet<>(nodeKeys);
        nextKeys.add(checkedKey);
        return new PipelineJobPlan<>(inputType, checkedPipeline.outputType(), nextNodes, nextKeys, checkedKey);
    }

    /**
     * 从当前终点启动两条 Pipeline；调用方必须继续声明合并 Pipeline。
     *
     * @param <L> 左分支输出类型
     * @param <R> 右分支输出类型
     * @param leftKey 左分支节点名称
     * @param left 左分支 Pipeline
     * @param leftRetry 左分支重试策略
     * @param rightKey 右分支节点名称
     * @param right 右分支 Pipeline
     * @param rightRetry 右分支重试策略
     * @return 只能通过 merge 完成的二路分支
     * @throws NullPointerException 任一节点名称、Pipeline 或重试策略为空时抛出
     * @throws IllegalArgumentException 节点名称无效、重复，或分支输入类型不能承接当前输出时抛出
     */
    @CheckReturnValue
    @Contract(pure = true)
    public <L, R> @NotNull Parallel<I, L, R> parallel(
            @NotNull String leftKey,
            @NotNull Pipeline<O, L> left,
            @NotNull JobRetryPolicy leftRetry,
            @NotNull String rightKey,
            @NotNull Pipeline<O, R> right,
            @NotNull JobRetryPolicy rightRetry) {
        Pipeline<O, L> checkedLeft = Objects.requireNonNull(left, "左分支 Pipeline 不能为空");
        Pipeline<O, R> checkedRight = Objects.requireNonNull(right, "右分支 Pipeline 不能为空");
        requireType(outputType, checkedLeft.inputType(), "左分支输入类型不一致");
        requireType(outputType, checkedRight.inputType(), "右分支输入类型不一致");
        String checkedLeftKey = requireNewKey(leftKey);
        String checkedRightKey = requireNewKey(rightKey);
        if (checkedLeftKey.equals(checkedRightKey)) {
            throw new IllegalArgumentException("PipelineJob 左右分支节点名称不能相同");
        }
        return new Parallel<>(
                this,
                checkedLeftKey,
                checkedLeft,
                Objects.requireNonNull(leftRetry, "左分支重试策略不能为空"),
                checkedRightKey,
                checkedRight,
                Objects.requireNonNull(rightRetry, "右分支重试策略不能为空"));
    }

    /**
     * 返回根输入类型。
     *
     * @return 创建计划时声明的具体类型
     */
    @Contract(pure = true)
    public @NotNull Class<I> inputType() {
        return inputType;
    }

    /**
     * 返回当前终点输出类型。
     *
     * @return 最后一个顺序或合并节点的输出类型
     */
    @Contract(pure = true)
    public @NotNull Class<O> outputType() {
        return outputType;
    }

    /**
     * 返回已经声明的节点。
     *
     * @return 按声明顺序排列的不可修改列表
     */
    @Contract(pure = true)
    public @Unmodifiable @NotNull List<@NotNull Node> nodes() {
        return nodes;
    }

    /** 拒绝当前计划已经使用的节点名称。 */
    private @NotNull String requireNewKey(@NotNull String nodeKey) {
        String checked = requireKey(nodeKey);
        if (nodeKeys.contains(checked)) {
            throw new IllegalArgumentException("PipelineJob 节点名称重复: " + checked);
        }
        return checked;
    }

    /** 校验 PipelineJob 节点名称。 */
    private static @NotNull String requireKey(@NotNull String nodeKey) {
        String checked = Objects.requireNonNull(nodeKey, "PipelineJob 节点名称不能为空").trim();
        if (checked.isEmpty() || !checked.equals(nodeKey)) {
            throw new IllegalArgumentException("PipelineJob 节点名称不能为空或包含首尾空白");
        }
        if (checked.length() > 128) {
            throw new IllegalArgumentException(
                    "PipelineJob 节点名称不能超过 128 个字符: length=" + checked.length());
        }
        return checked;
    }

    /** 补充检查泛型擦除或 raw type 调用造成的类型不一致。 */
    private static void requireType(
            @NotNull Class<?> expected,
            @NotNull Class<?> actual,
            @NotNull String message) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(
                    message + ": " + expected.getName() + " -> " + actual.getName());
        }
    }

    /** 保存一个可独立重试的 Pipeline 节点及其直接依赖。 */
    public static final class Node {

        private final @NotNull String key;

        private final @NotNull Pipeline<?, ?> pipeline;

        private final @NotNull JobRetryPolicy retryPolicy;

        private final @NotNull List<@NotNull String> dependencies;

        private final boolean parallelMerge;

        private Node(
                @NotNull String key,
                @NotNull Pipeline<?, ?> pipeline,
                @NotNull JobRetryPolicy retryPolicy,
                @NotNull List<@NotNull String> dependencies,
                boolean parallelMerge) {
            this.key = key;
            this.pipeline = pipeline;
            this.retryPolicy = retryPolicy;
            this.dependencies = List.copyOf(dependencies);
            this.parallelMerge = parallelMerge;
        }

        /**
         * 返回节点名称。
         *
         * @return 节点在当前 Job 内的稳定名称
         */
        @Contract(pure = true)
        public @NotNull String key() {
            return key;
        }

        /**
         * 返回节点处理线。
         *
         * @return 当前节点每次尝试完整执行的 Pipeline
         */
        @Contract(pure = true)
        public @NotNull Pipeline<?, ?> pipeline() {
            return pipeline;
        }

        /**
         * 返回节点重试规则。
         *
         * @return 当前节点自己的重试策略
         */
        @Contract(pure = true)
        public @NotNull JobRetryPolicy retryPolicy() {
            return retryPolicy;
        }

        /**
         * 返回直接依赖。
         *
         * @return 按输入组装顺序排列的节点名称
         */
        @Contract(pure = true)
        public @Unmodifiable @NotNull List<@NotNull String> dependencies() {
            return dependencies;
        }

        /**
         * 返回当前节点是否合并并发结果。
         *
         * @return 是否把两个依赖输出组合成 ParallelResults 后执行
         */
        @Contract(pure = true)
        public boolean parallelMerge() {
            return parallelMerge;
        }
    }

    /**
     * 保存尚未合并的两个并发 Pipeline，只有 merge 能返回新的完整计划。
     *
     * @param <I> 根输入类型
     * @param <L> 左分支输出类型
     * @param <R> 右分支输出类型
     */
    public static final class Parallel<I, L, R> {

        private final @NotNull PipelineJobPlan<I, ?> plan;

        private final @NotNull String leftKey;

        private final @NotNull Pipeline<?, L> left;

        private final @NotNull JobRetryPolicy leftRetry;

        private final @NotNull String rightKey;

        private final @NotNull Pipeline<?, R> right;

        private final @NotNull JobRetryPolicy rightRetry;

        private Parallel(
                @NotNull PipelineJobPlan<I, ?> plan,
                @NotNull String leftKey,
                @NotNull Pipeline<?, L> left,
                @NotNull JobRetryPolicy leftRetry,
                @NotNull String rightKey,
                @NotNull Pipeline<?, R> right,
                @NotNull JobRetryPolicy rightRetry) {
            this.plan = plan;
            this.leftKey = leftKey;
            this.left = left;
            this.leftRetry = leftRetry;
            this.rightKey = rightKey;
            this.right = right;
            this.rightRetry = rightRetry;
        }

        /**
         * 增加左右分支和等待二者成功的合并 Pipeline。
         *
         * @param <N> 合并节点输出类型
         * @param mergeKey 合并节点名称
         * @param merge 接受左右结果的合并 Pipeline
         * @param retryPolicy 合并 Pipeline 的重试策略
         * @return 以合并结果作为终点的完整计划
         * @throws NullPointerException 合并节点名称、Pipeline 或重试策略为空时抛出
         * @throws IllegalArgumentException 合并节点名称无效或重复，或合并 Pipeline 不接受 ParallelResults 时抛出
         */
        @CheckReturnValue
        @Contract(pure = true)
        public <N> @NotNull PipelineJobPlan<I, N> merge(
                @NotNull String mergeKey,
                @NotNull Pipeline<ParallelResults<L, R>, N> merge,
                @NotNull JobRetryPolicy retryPolicy) {
            String checkedMergeKey = plan.requireNewKey(mergeKey);
            if (leftKey.equals(checkedMergeKey) || rightKey.equals(checkedMergeKey)) {
                throw new IllegalArgumentException("PipelineJob 分支和合并节点名称不能重复");
            }
            Pipeline<ParallelResults<L, R>, N> checkedMerge = Objects.requireNonNull(merge, "合并 Pipeline 不能为空");
            requireType(ParallelResults.class, checkedMerge.inputType(), "合并 Pipeline 输入类型错误");

            List<Node> nextNodes = new ArrayList<>(plan.nodes);
            List<String> branchDependencies = plan.endpointKey == null
                    ? List.of()
                    : List.of(plan.endpointKey);
            nextNodes.add(new Node(leftKey, left, leftRetry, branchDependencies, false));
            nextNodes.add(new Node(rightKey, right, rightRetry, branchDependencies, false));
            nextNodes.add(
                    new Node(
                            checkedMergeKey,
                            checkedMerge,
                            Objects.requireNonNull(retryPolicy, "合并重试策略不能为空"),
                            List.of(leftKey, rightKey),
                            true));
            Set<String> nextKeys = new HashSet<>(plan.nodeKeys);
            nextKeys.add(leftKey);
            nextKeys.add(rightKey);
            nextKeys.add(checkedMergeKey);
            return new PipelineJobPlan<>(
                    plan.inputType,
                    checkedMerge.outputType(),
                    nextNodes,
                    nextKeys,
                    checkedMergeKey);
        }
    }
}
