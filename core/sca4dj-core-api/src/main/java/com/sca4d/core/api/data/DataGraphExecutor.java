package com.sca4d.core.api.data;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 按 DataGraph 的稳定拓扑顺序执行类型化同步数据图。 */
public final class DataGraphExecutor {

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
