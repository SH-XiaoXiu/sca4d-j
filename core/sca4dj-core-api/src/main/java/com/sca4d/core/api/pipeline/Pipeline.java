package com.sca4d.core.api.pipeline;

import com.sca4d.core.api.pipeline.exception.PipelineExecutionException;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * 按固定顺序把输入传过一组类型相接的 {@link PipelineStage}。
 *
 * <p>Pipeline 不创建线程、不保存运行状态，也不提供分支、等待、重试或汇合。
 *
 * @param <I> 整条 Pipeline 的输入类型
 * @param <O> 整条 Pipeline 的输出类型
 */
public class Pipeline<I, O> {

    private final @NotNull Class<I> inputType;

    private final @NotNull Class<O> outputType;

    private final @NotNull List<@NotNull PipelineStage<?, ?>> stages;

    private final @NotNull Set<@NotNull String> stageCodes;

    private Pipeline(
            @NotNull List<@NotNull PipelineStage<?, ?>> stages,
            @NotNull Set<@NotNull String> stageCodes,
            @NotNull Class<I> inputType,
            @NotNull Class<O> outputType) {
        this.stages = List.copyOf(stages);
        this.stageCodes = Set.copyOf(stageCodes);
        this.inputType = inputType;
        this.outputType = outputType;
    }

    /**
     * 创建不改变输入值的空 Pipeline。
     *
     * @param valueType 输入和输出使用的具体引用类型
     * @param <T> 输入和输出类型
     * @return 不包含处理阶段的新 Pipeline
     */
    @CheckReturnValue
    @Contract("_ -> new")
    public static <T> @NotNull Pipeline<T, T> identity(@NotNull Class<T> valueType) {
        Class<T> checkedType = Objects.requireNonNull(valueType, "Pipeline 值类型不能为空");
        if (Void.class.equals(checkedType)) {
            throw new IllegalArgumentException("Pipeline 值类型不能使用 Void");
        }
        return new Pipeline<>(List.of(), Set.of(), checkedType, checkedType);
    }

    /**
     * 使用第一个类型化阶段创建 Pipeline。
     *
     * @param <I> 第一阶段输入类型
     * @param <O> 第一阶段输出类型
     * @param stage 第一个阶段
     * @return 输入输出类型由阶段确定的新 Pipeline
     * @throws NullPointerException 阶段、阶段名称或阶段输入输出类型为空时抛出
     * @throws IllegalArgumentException 阶段名称为空或包含首尾空白时抛出
     */
    @CheckReturnValue
    @Contract(pure = true)
    public static <I, O> @NotNull Pipeline<I, O> of(@NotNull PipelineStage<I, O> stage) {
        PipelineStage<I, O> checked = requireStage(stage);
        return new Pipeline<>(
                List.of(checked),
                Set.of(checked.code()),
                checked.inputType(),
                checked.outputType());
    }

    /**
     * 在尾部追加一个输入类型与当前输出类型相同的阶段。
     *
     * @param <N> 新阶段输出类型
     * @param stage 新阶段
     * @return 保留原输入类型并采用新输出类型的新 Pipeline
     * @throws NullPointerException 阶段、阶段名称或阶段输入输出类型为空时抛出
     * @throws IllegalArgumentException 阶段类型不能承接当前输出，或阶段名称为空、重复、包含首尾空白时抛出
     */
    @CheckReturnValue
    @Contract(pure = true)
    public <N> @NotNull Pipeline<I, N> then(@NotNull PipelineStage<O, N> stage) {
        PipelineStage<O, N> checked = requireStage(stage);
        if (!outputType.equals(checked.inputType())) {
            throw new IllegalArgumentException(
                    "Pipeline 相邻阶段类型不一致: " + outputType.getName() + " -> " + checked.inputType().getName());
        }
        if (stageCodes.contains(checked.code())) {
            throw new IllegalArgumentException("Pipeline 阶段名称重复: " + checked.code());
        }
        List<PipelineStage<?, ?>> nextStages = new ArrayList<>(stages);
        nextStages.add(checked);
        Set<String> nextCodes = new HashSet<>(stageCodes);
        nextCodes.add(checked.code());
        return new Pipeline<>(nextStages, nextCodes, inputType, checked.outputType());
    }

    /**
     * 从第一阶段开始同步处理输入，并返回最后一个阶段的输出。
     *
     * @param input 非空 Pipeline 输入
     * @return 最后阶段产生的非空输出
     * @throws NullPointerException 输入为空时抛出
     * @throws IllegalArgumentException 泛型被绕过且实际输入类型不一致时抛出
     * @throws PipelineExecutionException 阶段抛出运行时异常、返回空值或返回声明类型以外的值时抛出；
     *     {@link PipelineExecutionException#stageCode()} 指向失败阶段
     */
    @CheckReturnValue
    @SuppressWarnings("unchecked")
    public @NotNull O run(@NotNull I input) {
        Object value = Objects.requireNonNull(input, "Pipeline 输入不能为空");
        if (!inputType.isInstance(value)) {
            throw new IllegalArgumentException("Pipeline 输入类型不一致: " + value.getClass().getName());
        }
        for (PipelineStage<?, ?> stage : stages) {
            PipelineStage<Object, Object> current = (PipelineStage<Object, Object>) stage;
            try {
                value = Objects.requireNonNull(
                        current.execute(value),
                        "Pipeline 阶段不能返回 null: " + current.code());
                if (!current.outputType().isInstance(value)) {
                    throw new IllegalStateException(
                            "Pipeline 阶段输出类型不一致: " + current.code()
                                    + ", expected=" + current.outputType().getName()
                                    + ", actual=" + value.getClass().getName());
                }
            } catch (PipelineExecutionException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new PipelineExecutionException(current.code(), exception);
            }
        }
        return (O) value;
    }

    /** 按同一阶段顺序异步转换；后一阶段只在前一阶段成功完成后执行。 */
    @CheckReturnValue
    @SuppressWarnings("unchecked")
    public @NotNull CompletionStage<O> runAsync(@NotNull I input, @NotNull Executor executor) {
        Objects.requireNonNull(executor, "Pipeline 执行器不能为空");
        if (!inputType.isInstance(Objects.requireNonNull(input))) {
            throw new IllegalArgumentException("Pipeline 输入类型不一致");
        }
        CompletionStage<Object> result = CompletableFuture.completedFuture(input);
        for (PipelineStage<?, ?> stage : stages) {
            PipelineStage<Object, Object> current = (PipelineStage<Object, Object>) stage;
            result = result.thenCompose(value -> {
                CompletionStage<Object> output;
                try {
                    output = current.executeAsync(value, executor);
                } catch (Throwable failure) {
                    output = CompletableFuture.failedFuture(failure);
                }
                return output.handle((converted, failure) -> {
                    Throwable cause = failure instanceof CompletionException ? failure.getCause() : failure;
                    if (cause instanceof Error error) {
                        throw error;
                    }
                    if (cause instanceof PipelineExecutionException pipelineFailure) {
                        throw pipelineFailure;
                    }
                    if (cause != null) {
                        throw new PipelineExecutionException(
                                current.code(),
                                cause instanceof RuntimeException runtime
                                        ? runtime
                                        : new IllegalStateException(cause));
                    }
                    if (!current.outputType().isInstance(converted)) {
                        throw new PipelineExecutionException(
                                current.code(),
                                new IllegalStateException("Pipeline 阶段返回空值或类型不匹配"));
                    }
                    return converted;
                });
            });
        }
        return result.thenApply(value -> (O) value);
    }

    /**
     * 整条 Pipeline 的输入类型。
     *
     * @return 第一阶段要求的具体输入类型
     */
    @Contract(pure = true)
    public @NotNull Class<I> inputType() {
        return inputType;
    }

    /**
     * 整条 Pipeline 的输出类型。
     *
     * @return 最后阶段产生的具体输出类型
     */
    @Contract(pure = true)
    public @NotNull Class<O> outputType() {
        return outputType;
    }

    /** 校验阶段对象和稳定名称。 */
    private static <I, O> @NotNull PipelineStage<I, O> requireStage(@NotNull PipelineStage<I, O> stage) {
        PipelineStage<I, O> checked = Objects.requireNonNull(stage, "Pipeline 阶段不能为空");
        String code = Objects.requireNonNull(checked.code(), "Pipeline 阶段名称不能为空").trim();
        if (code.isEmpty()) {
            throw new IllegalArgumentException("Pipeline 阶段名称不能为空");
        }
        if (!code.equals(checked.code())) {
            throw new IllegalArgumentException("Pipeline 阶段名称不能包含首尾空白: " + checked.code());
        }
        Class<I> inputType = Objects.requireNonNull(checked.inputType(), "Pipeline 阶段输入类型不能为空");
        Class<O> outputType = Objects.requireNonNull(checked.outputType(), "Pipeline 阶段输出类型不能为空");
        if (Void.class.equals(inputType) || Void.class.equals(outputType)) {
            throw new IllegalArgumentException("Pipeline 阶段输入输出不能使用 Void");
        }
        return checked;
    }
}
