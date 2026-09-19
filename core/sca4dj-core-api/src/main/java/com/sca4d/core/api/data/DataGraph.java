package com.sca4d.core.api.data;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 保存已经校验并按稳定拓扑顺序排列的数据操作图。 */
public final class DataGraph {

    private final List<DataKey<?>> inputs;

    private final List<DataOperation<?>> operations;

    /** 创建已经完成结构校验的数据图。 */
    private DataGraph(List<DataKey<?>> inputs, List<DataOperation<?>> operations) {
        this.inputs = List.copyOf(inputs);
        this.operations = List.copyOf(operations);
    }

    /**
     * 开始声明输入键和计算节点。
     *
     * @return 尚未包含输入和节点的新构建器
     */
    @CheckReturnValue
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * 读取执行 DataGraph 前必须由调用方提供的键。
     *
     * @return 保持声明顺序的不可变输入键列表
     */
    public @Unmodifiable @NotNull List<DataKey<?>> inputs() {
        return inputs;
    }

    /**
     * 读取构建阶段完成依赖校验和排序后的节点。
     *
     * @return 按节点登记顺序确定拓扑次序的不可变节点列表
     */
    public @Unmodifiable @NotNull List<DataOperation<?>> operations() {
        return operations;
    }

    /** 收集输入和输出名称并拒绝同名键。 */
    private static Map<String, DataKey<?>> keys(
            List<DataKey<?>> inputs,
            List<DataOperation<?>> operations) {
        Map<String, DataKey<?>> keys = new LinkedHashMap<>();
        for (DataKey<?> input : inputs) {
            putKey(keys, input, "重复 Graph 输入键");
        }
        for (DataOperation<?> operation : operations) {
            putKey(keys, operation.output(), "重复 Graph 输出键");
        }
        return keys;
    }

    /** 向名称索引登记键并拒绝相同名称。 */
    private static void putKey(Map<String, DataKey<?>> keys, DataKey<?> key, String message) {
        DataKey<?> previous = keys.putIfAbsent(key.name(), key);
        if (previous != null) {
            throw new IllegalArgumentException(message + ": " + key.name());
        }
    }

    /** 检查每个节点只引用 Graph 已声明且类型一致的键。 */
    private static void validateDependencies(
            Map<String, DataKey<?>> keys,
            List<DataOperation<?>> operations) {
        for (DataOperation<?> operation : operations) {
            Set<String> names = new HashSet<>();
            for (DataKey<?> input : operation.inputs()) {
                if (!names.add(input.name())) {
                    throw new IllegalArgumentException(
                            "数据操作包含重复输入: " + operation.name() + "/" + input.name());
                }
                DataKey<?> declared = keys.get(input.name());
                if (declared == null) {
                    throw new IllegalArgumentException(
                            "数据操作引用未声明输入: " + operation.name() + "/" + input.name());
                }
                if (!declared.equals(input)) {
                    throw new IllegalArgumentException(
                            "数据操作输入类型不一致: " + operation.name() + "/" + input.name());
                }
            }
        }
    }

    /** 按节点登记顺序执行稳定拓扑排序并拒绝环。 */
    private static List<DataOperation<?>> topological(
            List<DataKey<?>> inputs,
            List<DataOperation<?>> operations) {
        Set<DataKey<?>> available = new LinkedHashSet<>(inputs);
        List<DataOperation<?>> remaining = new ArrayList<>(operations);
        List<DataOperation<?>> sorted = new ArrayList<>(operations.size());
        while (!remaining.isEmpty()) {
            boolean progressed = false;
            for (int index = 0; index < remaining.size();) {
                DataOperation<?> operation = remaining.get(index);
                if (!available.containsAll(operation.inputs())) {
                    index++;
                    continue;
                }
                sorted.add(operation);
                available.add(operation.output());
                remaining.remove(index);
                progressed = true;
            }
            if (!progressed) {
                throw new IllegalArgumentException(
                        "DataGraph 存在环: " + remaining.stream().map(DataOperation::name).toList());
            }
        }
        return sorted;
    }

    /** 按登记顺序声明 Graph 输入和数据操作。 */
    public static final class Builder {

        private final List<DataKey<?>> inputs = new ArrayList<>();

        private final List<DataOperation<?>> operations = new ArrayList<>();

        /** 创建空 Graph 构建器。 */
        public Builder() {
        }

        /**
         * 登记调用方必须提供的输入。
         *
         * @param key 输入键
         * @return 当前构建器
         */
        public @NotNull Builder input(@NotNull DataKey<?> key) {
            inputs.add(Objects.requireNonNull(key, "Graph 输入键不能为空"));
            return this;
        }

        /**
         * 登记一个数据操作。
         *
         * @param operation 数据操作
         * @return 当前构建器
         */
        public @NotNull Builder operation(@NotNull DataOperation<?> operation) {
            operations.add(Objects.requireNonNull(operation, "Graph 数据操作不能为空"));
            return this;
        }

        /**
         * 校验键、节点名称、依赖和环并创建不可变 Graph。
         *
         * @return 已按稳定拓扑顺序排列的数据图
         */
        @CheckReturnValue
        public @NotNull DataGraph build() {
            Set<String> operationNames = new HashSet<>();
            for (DataOperation<?> operation : operations) {
                if (!operationNames.add(operation.name())) {
                    throw new IllegalArgumentException("重复数据操作名称: " + operation.name());
                }
            }
            Map<String, DataKey<?>> keys = keys(inputs, operations);
            validateDependencies(keys, operations);
            return new DataGraph(inputs, topological(inputs, operations));
        }
    }
}
