package com.sca4d.core.api.permission;

import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.resource.Resource;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 表示能够被权限判断和审计的固定业务入口（Operation）。
 *
 * <p>操作本身就是一种资源（Resource，{@link Resource}），因此接口权限与具体业务对象权限都由 {@link Permission} 表示。
 */
@SuppressWarnings("ClassCanBeRecord") // 项目禁止使用 Java record，保留普通 class。
public final class Operation implements Resource {

    /** 操作资源使用的资源类型。 */
    public static final @NotNull String RESOURCE_TYPE = "CORE/OPERATIONS";

    private final @NotNull String value;

    /**
     * 创建操作资源。
     *
     * @param value 非空白操作键
     * @throws NullPointerException value 为 {@code null} 时抛出
     * @throws IllegalArgumentException value 为空白时抛出
     */
    public Operation(@NotNull String value) {
        this.value = normalize(value);
    }

    /**
     * 返回操作键。
     *
     * @return 操作键
     */
    public @NotNull String value() {
        return value;
    }

    /**
     * 返回操作资源类型。
     *
     * @return 固定值 {@value #RESOURCE_TYPE}
     */
    @Override
    public @NotNull String type() {
        return RESOURCE_TYPE;
    }

    /**
     * 返回操作资源标识。
     *
     * @return 与 {@link #value()} 相同的操作键
     */
    @Override
    public @NotNull String id() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Operation operation && value.equals(operation.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public @NotNull String toString() {
        return value;
    }

    /** 规范化具体操作或末段为通配符的权限选择器。 */
    static @NotNull String normalize(@NotNull String value) {
        String checked = Objects.requireNonNull(value, "操作键不能为空").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException("操作键不能为空白");
        }
        int wildcard = checked.indexOf('*');
        if (wildcard < 0) {
            return TreeCode.of(checked).value();
        }
        if (wildcard != checked.length() - 1 || checked.indexOf('*', wildcard + 1) >= 0) {
            throw new IllegalArgumentException("操作通配符只能位于最后一个层级");
        }
        int separator = Math.max(checked.lastIndexOf(':'), checked.lastIndexOf('/'));
        if (separator <= 0 || separator == checked.length() - 1) {
            throw new IllegalArgumentException("操作通配符必须跟随完整资源层级");
        }
        char delimiter = checked.charAt(separator);
        String prefix = TreeCode.of(checked.substring(0, separator)).value();
        return prefix + delimiter + '*';
    }
}
