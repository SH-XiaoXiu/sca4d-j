package com.sca4d.core.api.audit;

import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.resource.Resource;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 表示审计记录使用的稳定业务操作，不参与权限判断。
 */
@SuppressWarnings("ClassCanBeRecord") // 项目禁止使用 Java record，保留普通 class。
public final class AuditOperation implements Resource {

    /**
     * 审计操作使用的资源类型。
     */
    public static final @NotNull TreeCode RESOURCE_TYPE = TreeCode.ofSlash("AUDIT/OPERATIONS");

    private final @NotNull TreeCode code;

    /**
     * 创建审计操作。
     *
     * @param value 已持久化或新声明的操作键
     * @throws NullPointerException     value 为空时抛出
     * @throws IllegalArgumentException 操作键为空白时抛出
     */
    public AuditOperation(@NotNull String value) {
        this(TreeCode.ofSlash(value));
    }

    /**
     * 创建使用已经规范化审计操作编码的审计操作。
     */
    public AuditOperation(@NotNull TreeCode code) {
        TreeCode checked = Objects.requireNonNull(code, "审计操作不能为空");
        if (checked.separator() != '/') {
            throw new IllegalArgumentException("审计操作必须使用斜杠分层: " + checked);
        }
        this.code = checked;
    }

    /**
     * 校验并创建业务代码中新声明的层级审计操作。
     *
     * @param value 使用大写字母、数字或下划线组成的层级操作键
     * @return 已校验的审计操作
     * @throws NullPointerException     value 为空时抛出
     * @throws IllegalArgumentException 操作键不符合层级命名规则时抛出
     */
    public static @NotNull AuditOperation declared(@NotNull String value) {
        return new AuditOperation(TreeCode.ofSlash(value));
    }

    /**
     * @return 稳定审计操作 TreeCode
     */
    public @NotNull TreeCode code() {
        return code;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull TreeCode type() {
        return RESOURCE_TYPE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull String id() {
        return code.value();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof AuditOperation operation && code.equals(operation.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code);
    }

    @Override
    public @NotNull String toString() {
        return code.toString();
    }
}
