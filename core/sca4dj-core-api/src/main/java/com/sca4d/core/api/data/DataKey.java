package com.sca4d.core.api.data;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 使用固定名称和运行时类型标识一个数据值，供 DataGraph 输入输出和 Call 入口快照使用。
 *
 * @param <T> 该键允许读写的值类型
 */
@SuppressWarnings("ClassCanBeRecord")
public class DataKey<T> {

    private final String name;

    private final Class<T> type;

    /**
     * 创建类型化数据键。
     *
     * @param name Graph 内唯一的稳定名称
     * @param type 该键接受的运行时引用类型
     */
    public DataKey(@NotNull String name, @NotNull Class<T> type) {
        this.name = TextUtils.requireText(name, "数据键名称");
        this.type = Objects.requireNonNull(type, "数据键类型不能为空");
        if (type.isPrimitive()) {
            throw new IllegalArgumentException("数据键必须使用引用类型: " + type.getName());
        }
    }

    /**
     * 创建类型化数据键。
     *
     * @param name Graph 内唯一的稳定名称
     * @param type 该键接受的运行时引用类型
     * @param <T> 值类型
     * @return 新数据键
     */
    @CheckReturnValue
    public static <T> @NotNull DataKey<T> of(@NotNull String name, @NotNull Class<T> type) {
        return new DataKey<>(name, type);
    }

    /**
     * 读取该键在 DataGraph 中的名称。
     *
     * @return DataGraph 内唯一名称
     */
    public @NotNull String name() {
        return name;
    }

    /**
     * 读取执行器校验输入和节点输出时使用的类型。
     *
     * @return 该键接受的运行时类型
     */
    public @NotNull Class<T> type() {
        return type;
    }

    /**
     * 检查并转换该键对应的值。
     *
     * @param value 待检查值
     * @return 转换后的类型化值
     */
    @NotNull
    T cast(@NotNull Object value) {
        return type.cast(Objects.requireNonNull(value, "数据值不能为空"));
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof DataKey<?> key && name.equals(key.name) && type.equals(key.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type);
    }

    @Override
    public String toString() {
        return name + ":" + type.getName();
    }
}
