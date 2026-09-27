package com.sca4d.core.api.setting;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 保存一次批量查询得到的不可变设置快照。 */
public class SettingValues {

    private final Map<String, SettingValue<?>> values;

    /**
     * 创建设置快照。
     *
     * @param values 同一次数据库查询转换得到的设置值
     */
    public SettingValues(@NotNull Collection<? extends SettingValue<?>> values) {
        Map<String, SettingValue<?>> indexed = new LinkedHashMap<>();
        for (SettingValue<?> value : Objects.requireNonNull(values, "设置值不能为空")) {
            SettingValue<?> existing = indexed.putIfAbsent(value.setting().key(), value);
            if (existing != null) {
                throw new IllegalArgumentException("设置快照包含重复键: " + value.setting().key());
            }
        }
        this.values = Map.copyOf(indexed);
    }

    /**
     * 按类型化定义读取当前快照中的值。
     *
     * @param setting 查询快照时使用的设置定义
     * @param <T> 设置值类型
     * @return 数据库值或 Java 默认值
     * @throws IllegalArgumentException 当前快照不包含该设置时抛出
     */
    @CheckReturnValue
    public <T> @NotNull T get(@NotNull SystemSetting<T> setting) {
        return value(setting).value();
    }

    /**
     * 按类型化定义读取当前快照中的完整值和来源。
     *
     * @param setting 查询快照时使用的设置定义
     * @param <T> 设置值类型
     * @return 设置值和维护来源
     * @throws IllegalArgumentException 当前快照不包含该设置时抛出
     */
    @CheckReturnValue
    @SuppressWarnings("unchecked")
    public <T> @NotNull SettingValue<T> value(@NotNull SystemSetting<T> setting) {
        SystemSetting<T> checked = Objects.requireNonNull(setting, "设置定义不能为空");
        SettingValue<?> value = values.get(checked.key());
        if (value == null || value.setting() != checked) {
            throw new IllegalArgumentException("设置快照不包含定义: " + checked.key());
        }
        return (SettingValue<T>) value;
    }

    /** @return 快照包含的不可修改设置值集合 */
    @CheckReturnValue
    public @NotNull @Unmodifiable Collection<@NotNull SettingValue<?>> values() {
        return values.values();
    }
}
