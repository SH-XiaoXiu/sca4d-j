package com.sca4d.system;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.regex.Pattern;

/** 经过格式校验、但不封闭具体取值的 Lucide 图标名称。 */
public final class MenuIconName {

    private static final Pattern FORMAT = Pattern.compile("[A-Z][A-Za-z0-9]{0,63}");

    private final String value;

    private MenuIconName(@NotNull String value) {
        this.value = value;
    }

    /**
     * 从管理端提交或代码目录创建图标名称。
     *
     * @param value Lucide 导出的 PascalCase 名称
     * @return 已完成格式校验的开放图标名称
     */
    @JsonCreator
    public static @NotNull MenuIconName of(@NotNull String value) {
        String checked = value.trim();
        if (!FORMAT.matcher(checked).matches()) {
            throw new IllegalArgumentException("菜单图标名称格式错误: " + value);
        }
        return new MenuIconName(checked);
    }

    /** @return 写入数据库和 JSON 响应的 Lucide 图标名称 */
    @JsonValue
    public @NotNull String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof MenuIconName name && value.equals(name.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public @NotNull String toString() {
        return value;
    }
}
