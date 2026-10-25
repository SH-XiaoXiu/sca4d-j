package com.sca4d.core.api.data;

import com.sca4d.core.api.data.exception.DataAssertionException;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 声明一个只读取指定输入并产生唯一非空输出的数据操作。
 *
 * <p>操作可以是同步计算，也可以是由调用方 Executor 调度的异步只读操作；操作不能修改共享业务状态。
 *
 * @param <T> 节点写入输出键的值类型
 */
public class DataOperation<T> {

    private final BiFunction<DataValues, Executor, CompletionStage<T>> asyncAction;

    private final DataKey<T> output;

    private final Function<DataValues, T> action;

    private final List<DataKey<?>> inputs;

    private final String name;

    /**
     * 创建数据操作。
     *
     * @param name Graph 内唯一的节点名称
     * @param inputs 节点允许读取的输入键
     * @param output 唯一输出键
     * @param action 不修改外部状态的计算函数
     */
    public DataOperation(
            @NotNull String name,
            @NotNull List<DataKey<?>> inputs,
            @NotNull DataKey<T> output,
            @NotNull Function<DataValues, T> action) {
        this(name, inputs, output, Objects.requireNonNull(action, "数据操作函数不能为空"), null);
    }

    private DataOperation(
            @NotNull String name,
            @NotNull List<DataKey<?>> inputs,
            @NotNull DataKey<T> output,
            @Nullable Function<DataValues, T> action,
            @Nullable BiFunction<DataValues, Executor, CompletionStage<T>> asyncAction) {
        this.name = TextUtils.requireText(name, "数据操作名称");
        this.inputs = List.copyOf(Objects.requireNonNull(inputs, "数据操作输入不能为空"));
        if (this.inputs.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("数据操作输入不能包含 null");
        }
        this.output = Objects.requireNonNull(output, "数据操作输出不能为空");
        if (action == null && asyncAction == null) {
            throw new NullPointerException("数据操作必须提供同步或异步函数");
        }
        this.action = action;
        this.asyncAction = asyncAction;
    }

    /**
     * 创建数据操作。
     *
     * @param name Graph 内唯一的节点名称
     * @param inputs 节点允许读取的输入键
     * @param output 唯一输出键
     * @param action 不修改外部状态的计算函数
     * @param <T> 输出类型
     * @return 新数据操作
     */
    @CheckReturnValue
    public static <T> @NotNull DataOperation<T> of(
            @NotNull String name,
            @NotNull List<DataKey<?>> inputs,
            @NotNull DataKey<T> output,
            @NotNull Function<DataValues, T> action) {
        return new DataOperation<>(name, inputs, output, action);
    }

    /** 创建由调用方 Executor 调度的异步数据操作。 */
    @CheckReturnValue
    public static <T> @NotNull DataOperation<T> async(
            @NotNull String name,
            @NotNull List<DataKey<?>> inputs,
            @NotNull DataKey<T> output,
            @NotNull BiFunction<DataValues, Executor, CompletionStage<T>> action) {
        return new DataOperation<>(
                name,
                inputs,
                output,
                null,
                Objects.requireNonNull(action, "异步数据操作不能为空"));
    }

    /**
     * 诊断和执行结果使用的节点名称。
     *
     * @return DataGraph 内唯一节点名称
     */
    public @NotNull String name() {
        return name;
    }

    /**
     * 执行该节点前必须已经存在的输入键。
     *
     * @return 保持声明顺序的不可变输入键列表
     */
    public @Unmodifiable @NotNull List<DataKey<?>> inputs() {
        return inputs;
    }

    /**
     * 该节点计算完成后写入的键。
     *
     * @return 节点唯一输出键
     */
    public @NotNull DataKey<T> output() {
        return output;
    }

    /**
     * 同步执行节点函数并检查输出类型；只有异步操作时，在当前线程阻塞等待其完成。
     *
     * @param values 节点允许读取的输入快照
     * @return 通过类型检查的非空输出
     * @throws DataAssertionException 输出为 null 或类型与输出键不一致时抛出
     */
    @NotNull
    T execute(@NotNull DataValues values) {
        T result;
        if (action != null) {
            result = action.apply(values);
        } else {
            result = asyncAction.apply(values, Runnable::run).toCompletableFuture().join();
        }
        return checkOutput(result);
    }

    /** 异步执行节点函数并检查输出类型。 */
    @NotNull
    CompletionStage<T> executeAsync(@NotNull DataValues values, @NotNull Executor executor) {
        if (asyncAction == null) {
            return CompletableFuture.completedFuture(execute(values));
        }
        return Objects.requireNonNull(asyncAction.apply(values, executor), "异步数据操作不能返回 null")
                .thenApply(this::checkOutput);
    }

    /**
     * 拒绝 null 输出，并把输出转换为输出键声明的类型。
     *
     * @param result 节点函数返回的原始结果
     * @return 通过类型检查的输出
     * @throws DataAssertionException 结果为 null 或类型与输出键不一致时抛出
     */
    private @NotNull T checkOutput(T result) {
        if (result == null) {
            throw new DataAssertionException("数据操作不能返回 null");
        }
        try {
            return output.cast(result);
        } catch (ClassCastException exception) {
            throw new DataAssertionException("数据操作输出类型与声明不一致");
        }
    }
}
