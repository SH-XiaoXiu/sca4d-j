package com.sca4d.core.api.data;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 保存以 `DataKey` 登记的不可变类型化值。 */
public final class DataValues {

    private final Map<DataKey<?>, Object> values;

    /** 创建保持登记顺序的不可变值集合。 */
    private DataValues(Map<DataKey<?>, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /**
     * 创建数据值构建器。
     *
     * @return 新构建器
     */
    @CheckReturnValue
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * 读取指定键的非空值。
     *
     * @param key 已声明的数据键
     * @param <T> 值类型
     * @return 对应值
     * @throws DataAssertionException 当前集合没有该键时抛出
     */
    public <T> @NotNull T get(@NotNull DataKey<T> key) {
        Objects.requireNonNull(key, "数据键不能为空");
        Object value = values.get(key);
        if (value == null) {
            throw new DataAssertionException("缺少数据值: " + key.name());
        }
        return key.cast(value);
    }

    /**
     * 读取可能没有登记的类型化值。
     *
     * @param key 待读取的数据键
     * @param <T> 值类型
     * @return 当前集合包含该键时返回对应值，否则返回空
     */
    public <T> @NotNull Optional<T> find(@NotNull DataKey<T> key) {
        Objects.requireNonNull(key, "数据键不能为空");
        Object value = values.get(key);
        return value == null ? Optional.empty() : Optional.of(key.cast(value));
    }

    /**
     * 读取当前输入和节点结果的只读快照。
     *
     * @return 保持登记顺序的不可变值映射
     */
    public @Unmodifiable @NotNull Map<DataKey<?>, Object> asMap() {
        return values;
    }

    /** 在执行器内部创建值集合。 */
    static DataValues copyOf(Map<DataKey<?>, Object> values) {
        return new DataValues(values);
    }

    /** 按登记顺序创建非空类型化值。 */
    public static final class Builder {

        private final Map<DataKey<?>, Object> values = new LinkedHashMap<>();

        /** 创建空构建器。 */
        public Builder() {
        }

        /**
         * 登记一个非空值。
         *
         * @param key 数据键
         * @param value 与键类型一致的值
         * @param <T> 值类型
         * @return 当前构建器
         */
        public <T> @NotNull Builder put(@NotNull DataKey<T> key, @NotNull T value) {
            Objects.requireNonNull(key, "数据键不能为空");
            Object checked = key.cast(value);
            if (values.putIfAbsent(key, checked) != null) {
                throw new IllegalArgumentException("重复数据键: " + key.name());
            }
            return this;
        }

        /**
         * 创建不可变值集合。
         *
         * @return 当前已登记值
         */
        @CheckReturnValue
        public @NotNull DataValues build() {
            return new DataValues(values);
        }
    }
}
