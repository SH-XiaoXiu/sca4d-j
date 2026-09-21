package com.sca4d.core.api.audit;

import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.resource.Resource;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 表示审计记录使用的稳定业务操作，不参与权限判断。 */
@SuppressWarnings("ClassCanBeRecord") // 项目禁止使用 Java record，保留普通 class。
public final class AuditOperation implements Resource {

    /** 审计操作使用的资源类型。 */
    public static final @NotNull String RESOURCE_TYPE = "AUDIT/OPERATIONS";

    private final @NotNull String value;

    /**
     * 创建审计操作。
     *
     * @param value 已持久化或新声明的操作键
     * @throws NullPointerException value 为空时抛出
     * @throws IllegalArgumentException 操作键为空白时抛出
     */
    public AuditOperation(@NotNull String value) {
        this.value = TreeCode.of(value).value();
    }

    /**
     * 校验并创建业务代码中新声明的层级审计操作。
     *
     * @param value 使用小写字母、数字或连字符组成的层级操作键
     * @return 已校验的审计操作
     * @throws NullPointerException value 为空时抛出
     * @throws IllegalArgumentException 操作键不符合层级命名规则时抛出
     */
    public static @NotNull AuditOperation declared(@NotNull String value) {
        return new AuditOperation(value);
    }

    /** @return 稳定审计操作键 */
    public @NotNull String value() {
        return value;
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull String type() {
        return RESOURCE_TYPE;
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull String id() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof AuditOperation operation && value.equals(operation.value);
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
