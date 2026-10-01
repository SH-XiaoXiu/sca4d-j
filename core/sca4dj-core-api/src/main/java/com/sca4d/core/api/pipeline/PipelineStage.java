package com.sca4d.core.api.pipeline;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 把一种输入转换成下一种输出，供 {@link Pipeline} 按声明顺序调用。
 *
 * @param <I> 输入类型
 * @param <O> 输出类型
 */
public interface PipelineStage<I, O> {

    /** 声明返回异步结果的阶段；同步调用在原线程等待，不传播事务或 ThreadLocal。 */
    static <I, O> @NotNull PipelineStage<I, O> async(
            @NotNull String code,
            @NotNull Class<I> inputType,
            @NotNull Class<O> outputType,
            @NotNull BiFunction<? super I, Executor, ? extends CompletionStage<O>> action) {
        Objects.requireNonNull(action);
        PipelineStage<I, O> metadata = of(code, inputType, outputType, value -> {
            throw new IllegalStateException("异步阶段不能使用同步占位函数");
        });
        return new PipelineStage<>() {
            @Override
            public @NotNull String code() {
                return metadata.code();
            }

            @Override
            public @NotNull Class<I> inputType() {
                return metadata.inputType();
            }

            @Override
            public @NotNull Class<O> outputType() {
                return metadata.outputType();
            }

            @Override
            public @NotNull O execute(@NotNull I input) {
                return executeAsync(input, Runnable::run).toCompletableFuture().join();
            }

            @Override
            public @NotNull CompletionStage<O> executeAsync(@NotNull I input, @NotNull Executor executor) {
                return Objects.requireNonNull(action.apply(input, executor), "异步阶段不能返回空结果");
            }
        };
    }

    /** 在调用方执行器中执行同步阶段，异步实现覆盖此方法以直接返回完成信号。 */
    default @NotNull CompletionStage<O> executeAsync(@NotNull I input, @NotNull Executor executor) {
        return CompletableFuture.supplyAsync(() -> execute(input), executor);
    }

    /**
     * 使用类型化函数创建一个同步处理阶段。
     *
     * @param code 阶段在同一 Pipeline 中使用的唯一稳定名称
     * @param inputType 函数接收的具体引用类型
     * @param outputType 函数返回的具体引用类型
     * @param action 当前线程中执行的非空转换函数
     * @param <I> 输入类型
     * @param <O> 输出类型
     * @return 保留名称、输入输出类型和函数的新阶段
     */
    @CheckReturnValue
    static <I, O> @NotNull PipelineStage<I, O> of(
            @NotNull String code,
            @NotNull Class<I> inputType,
            @NotNull Class<O> outputType,
            @NotNull Function<? super I, ? extends O> action) {
        String checkedCode = TextUtils.requireText(code, "Pipeline 阶段名称");
        Class<I> checkedInputType = Objects.requireNonNull(inputType, "Pipeline 阶段输入类型不能为空");
        Class<O> checkedOutputType = Objects.requireNonNull(outputType, "Pipeline 阶段输出类型不能为空");
        Function<? super I, ? extends O> checkedAction = Objects.requireNonNull(action, "Pipeline 阶段函数不能为空");
        return new PipelineStage<>() {
            @Override
            public @NotNull String code() {
                return checkedCode;
            }

            @Override
            public @NotNull Class<I> inputType() {
                return checkedInputType;
            }

            @Override
            public @NotNull Class<O> outputType() {
                return checkedOutputType;
            }

            @Override
            public @NotNull O execute(@NotNull I input) {
                return checkedAction.apply(input);
            }
        };
    }

    /**
     * 返回当前阶段在一条 Pipeline 内使用的稳定名称。
     *
     * @return 非空、不含首尾空白且在同一 Pipeline 中唯一的阶段名称
     */
    @Contract(pure = true)
    @NotNull
    String code();

    /**
     * 返回阶段输入类型。
     *
     * @return 调用当前阶段时要求的具体非空输入类型
     */
    @Contract(pure = true)
    @NotNull
    Class<I> inputType();

    /**
     * 返回阶段输出类型。
     *
     * @return 当前阶段成功后产生的具体非空输出类型
     */
    @Contract(pure = true)
    @NotNull
    Class<O> outputType();

    /**
     * 处理上一个阶段已经产生的输入。
     *
     * @param input 非空输入
     * @return 交给下一个阶段的非空输出
     * @throws RuntimeException 当前阶段无法产生输出时抛出；Pipeline 将其包装为 {@link PipelineExecutionException}
     */
    @NotNull
    O execute(@NotNull I input);
}
