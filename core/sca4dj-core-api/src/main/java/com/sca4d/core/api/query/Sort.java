package com.sca4d.core.api.query;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 表示一个可以由列表声明选用的安全排序字段名。
 *
 * <p>字段名只保存协议标识符，不保存数据库列或 SQL。具体列表仍需在 {@code ListDefinition} 和 Mapper 中分别声明允许字段和列映射。
 */
public final class Sort {

    /** 记录创建时间。 */
    public static final @NotNull Sort CREATED_AT = new Sort("CREATED_AT");

    /** 记录最近修改时间。 */
    public static final @NotNull Sort UPDATED_AT = new Sort("UPDATED_AT");

    /** 使用 ASCII 字段保存的稳定名称，不表示本地化展示名称。 */
    public static final @NotNull Sort NAME = new Sort("NAME");

    /** 数据库标识、序号或同类稳定流水字段。 */
    public static final @NotNull Sort SERIAL = new Sort("SERIAL");

    private static final @NotNull Pattern NAME_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]*");

    private final @NotNull String name;

    private Sort(@NotNull String name) {
        this.name = name;
    }

    /**
     * 创建业务列表自己的排序字段。
     *
     * @param name 大写字母开头且只包含大写字母、数字和下划线的协议字段名
     * @return 可以登记到列表声明中的字段
     * @throws NullPointerException name 为空时抛出
     * @throws IllegalArgumentException name 不符合协议标识符格式时抛出
     */
    @CheckReturnValue
    @Contract(pure = true)
    public static @NotNull Sort of(@NotNull String name) {
        String checked = Objects.requireNonNull(name, "排序字段名不能为空");
        if (!NAME_PATTERN.matcher(checked).matches()) {
            throw new IllegalArgumentException("排序字段名必须是大写协议标识符");
        }
        return switch (checked) {
            case "CREATED_AT" -> CREATED_AT;
            case "UPDATED_AT" -> UPDATED_AT;
            case "NAME" -> NAME;
            case "SERIAL" -> SERIAL;
            default -> new Sort(checked);
        };
    }

    /** @return 当前字段的升序要求 */
    @CheckReturnValue
    @Contract(value = "-> new", pure = true)
    public @NotNull SortOrder asc() {
        return SortOrder.of(this, SortDir.ASC);
    }

    /** @return 当前字段的降序要求 */
    @CheckReturnValue
    @Contract(value = "-> new", pure = true)
    public @NotNull SortOrder desc() {
        return SortOrder.of(this, SortDir.DESC);
    }

    /** @return 对外查询参数使用的字段名 */
    @Contract(pure = true)
    public @NotNull String name() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Sort sort && name.equals(sort.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
