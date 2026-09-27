package com.sca4d.core.api.data;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 声明一个只读取指定输入并产生唯一非空输出的纯数据操作。
 *
 * @param <T> 节点写入输出键的值类型
 */
public class DataOperation<T> {

    private final String name;

    private final List<DataKey<?>> inputs;

    private final DataKey<T> output;

    private final Function<DataValues, T> action;

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
        this.name = TextUtils.requireText(name, "数据操作名称");
        this.inputs = List.copyOf(Objects.requireNonNull(inputs, "数据操作输入不能为空"));
        if (this.inputs.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("数据操作输入不能包含 null");
        }
        this.output = Objects.requireNonNull(output, "数据操作输出不能为空");
        this.action = Objects.requireNonNull(action, "数据操作函数不能为空");
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

    /**
     * 读取诊断和执行结果使用的节点名称。
     *
     * @return DataGraph 内唯一节点名称
     */
    public @NotNull String name() {
        return name;
    }

    /**
     * 读取执行该节点前必须已经存在的输入键。
     *
     * @return 保持声明顺序的不可变输入键列表
     */
    public @Unmodifiable @NotNull List<DataKey<?>> inputs() {
        return inputs;
    }

    /**
     * 读取该节点计算完成后写入的键。
     *
     * @return 节点唯一输出键
     */
    public @NotNull DataKey<T> output() {
        return output;
    }

    /** 执行节点函数并检查输出类型。 */
    @NotNull
    T execute(@NotNull DataValues values) {
        T result = action.apply(values);
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
