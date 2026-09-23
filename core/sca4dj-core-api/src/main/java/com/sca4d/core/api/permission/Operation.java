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

    /**
     * 操作资源使用的资源类型。
     */
    public static final @NotNull TreeCode RESOURCE_TYPE = TreeCode.ofColon("CORE:OPERATIONS");

    /**
     * 供 Java 注解参数使用的资源类型文本。
     */
    public static final @NotNull String RESOURCE_TYPE_VALUE = "CORE:OPERATIONS";

    private final @NotNull TreeCode code;

    /**
     * 创建操作资源。
     *
     * @param value 非空白操作键
     * @throws NullPointerException     value 为 {@code null} 时抛出
     * @throws IllegalArgumentException value 为空白时抛出
     */
    public Operation(@NotNull String value) {
        this(TreeCode.ofColon(value));
    }

    /**
     * 创建使用已经规范化操作编码的操作资源。
     */
    public Operation(@NotNull TreeCode code) {
        TreeCode checked = Objects.requireNonNull(code, "操作编码不能为空");
        if (checked.separator() != ':') {
            throw new IllegalArgumentException("Operation 必须使用冒号分层: " + checked);
        }
        this.code = checked;
    }

    /**
     * @return 已规范化的操作 TreeCode
     */
    public @NotNull TreeCode code() {
        return code;
    }

    /**
     * 返回操作资源类型。
     *
     * @return 固定值 {@value #RESOURCE_TYPE}
     */
    @Override
    public @NotNull TreeCode type() {
        return RESOURCE_TYPE;
    }

    /**
     * 返回操作资源标识。
     *
     * @return 与 {@link #value()} 相同的操作键
     */
    @Override
    public @NotNull String id() {
        return code.value();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Operation operation && code.equals(operation.code);
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public @NotNull String toString() {
        return code.toString();
    }
}
