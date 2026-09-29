package com.sca4d.core.api.data;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** 按 DataGraph 数据依赖同步或异步计算，成功后以稳定拓扑顺序返回完整结果。 */
public class DataGraphExecutor {

    /** 禁止创建无状态执行入口。 */
    private DataGraphExecutor() {
    }

    /**
     * 执行完整 Graph；任意节点失败时不返回部分结果。
     *
     * @param graph 已完成结构校验的数据图
     * @param inputs 与 Graph 输入完全一致的非空值
     * @return 包含输入和全部节点输出的不可变值集合
     */
    @CheckReturnValue
    public static @NotNull DataValues execute(
            @NotNull DataGraph graph,
            @NotNull DataValues inputs) {
        Objects.requireNonNull(graph, "DataGraph 不能为空");
        Objects.requireNonNull(inputs, "DataGraph 输入不能为空");
        validateInputs(graph, inputs);

        Map<DataKey<?>, Object> values = new LinkedHashMap<>(inputs.asMap());
        for (DataOperation<?> operation : graph.operations()) {
            Map<DataKey<?>, Object> visible = new LinkedHashMap<>();
            for (DataKey<?> input : operation.inputs()) {
                visible.put(input, values.get(input));
            }
            Object result;
            try {
                result = operation.execute(DataValues.copyOf(visible));
            } catch (DataAssertionException exception) {
                throw exception.at(operation);
            } catch (RuntimeException exception) {
                throw new DataOperationExecutionException(operation, exception);
            }
            values.put(operation.output(), result);
        }
        return DataValues.copyOf(values);
    }

    /**
     * 通过调用方提供的 Executor 异步执行无共享可变状态的数据节点。
     *
     * <p>节点只在直接输入全部成功后运行；任一失败立即结束返回值并取消未完成节点。
     * 取消不能强制终止已经运行的 Java 函数，节点必须遵守纯数据操作契约。
     * 不传播 CallHolder、事务或其他线程状态，不关闭调用方 Executor。
     *
     * @param graph 已校验的计算图
     * @param inputs 与图输入严格一致的不可变数据
     * @param executor 由调用方管理生命周期及容量的执行器
     * @return 可以取消的完整结果 Future，不返回部分结果
     */
    @CheckReturnValue
    public static @NotNull CompletableFuture<DataValues> executeAsync(
            @NotNull DataGraph graph,
            @NotNull DataValues inputs,
            @NotNull Executor executor) {
        Objects.requireNonNull(graph);
        Objects.requireNonNull(executor);
        validateInputs(graph, Objects.requireNonNull(inputs));
        AtomicBoolean stopped = new AtomicBoolean();
        CompletableFuture<DataValues> result = new CompletableFuture<>();
        Map<DataKey<?>, CompletableFuture<?>> values = new LinkedHashMap<>();
        inputs.asMap().forEach((key, value) -> values.put(key, CompletableFuture.completedFuture(value)));
        List<CompletableFuture<?>> tasks = new ArrayList<>();
        // 先登记每个输出的 Future；各节点只捕获自己的依赖，不并发写入结果集合。
        for (DataOperation<?> operation : graph.operations()) {
            Map<DataKey<?>, CompletableFuture<?>> dependencies = new LinkedHashMap<>();
            operation.inputs().forEach(key -> dependencies.put(key, values.get(key)));
            CompletableFuture<?> task = CompletableFuture.allOf(dependencies.values().toArray(CompletableFuture[]::new))
                    .thenComposeAsync(ignored -> {
                        if (stopped.get()) {
                            return CompletableFuture.failedFuture(new CancellationException("数据图已经停止"));
                        }
                        Map<DataKey<?>, Object> visible = new LinkedHashMap<>();
                        dependencies.forEach((key, future) -> visible.put(key, future.join()));
                        try {
                            return operation.executeAsync(DataValues.copyOf(visible), executor)
                                    .handle((value, failure) -> {
                                        if (failure == null) {
                                            return value;
                                        }
                                        Throwable cause = failure instanceof CompletionException
                                                ? failure.getCause()
                                                : failure;
                                        if (cause instanceof DataAssertionException assertion) {
                                            throw new CompletionException(assertion.at(operation));
                                        }
                                        if (cause instanceof RuntimeException runtimeException) {
                                            throw new CompletionException(
                                                    new DataOperationExecutionException(operation, runtimeException));
                                        }
                                        throw new CompletionException(cause);
                                    });
                        } catch (DataAssertionException exception) {
                            return CompletableFuture.failedFuture(exception.at(operation));
                        } catch (RuntimeException exception) {
                            return CompletableFuture.failedFuture(new DataOperationExecutionException(operation, exception));
                        }
                    }, executor);
            tasks.add(task);
            values.put(operation.output(), task);
            task.whenComplete((value, failure) -> {
                if (failure != null) {
                    stopped.set(true);
                    result.completeExceptionally(failure instanceof CompletionException ? failure.getCause() : failure);
                }
            });
        }
        // 结果顺序仅由图决定，与完成先后和线程调度无关。
        CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).whenComplete((ignored, failure) -> {
            if (failure == null) {
                Map<DataKey<?>, Object> completed = new LinkedHashMap<>();
                values.forEach((key, future) -> completed.put(key, future.join()));
                result.complete(DataValues.copyOf(completed));
            }
        });
        result.whenComplete((value, failure) -> {
            if (failure != null) {
                stopped.set(true);
                tasks.forEach(task -> task.cancel(true));
            }
        });
        return result;
    }

    /** 检查调用方只提供 Graph 声明的全部输入。 */
    private static void validateInputs(DataGraph graph, DataValues inputs) {
        Set<DataKey<?>> expected = new LinkedHashSet<>(graph.inputs());
        Set<DataKey<?>> actual = inputs.asMap().keySet();
        if (!actual.equals(expected)) {
            Set<String> missing = new LinkedHashSet<>();
            Set<String> unexpected = new LinkedHashSet<>();
            expected.stream()
                    .filter(key -> !actual.contains(key))
                    .map(DataKey::name)
                    .forEach(missing::add);
            actual.stream()
                    .filter(key -> !expected.contains(key))
                    .map(DataKey::name)
                    .forEach(unexpected::add);
            throw new DataAssertionException(
                    "DataGraph 输入不一致, missing=" + missing + ", unexpected=" + unexpected);
        }
    }
}
