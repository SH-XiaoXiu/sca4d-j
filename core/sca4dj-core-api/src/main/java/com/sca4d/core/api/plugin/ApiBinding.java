package com.sca4d.core.api.plugin;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import java.util.Map;
import java.util.Objects;

/** 把公开契约绑定到生成的直接调用分发器。 */
public final class ApiBinding {
    private final @NotNull ApiDefinition definition;
    private final @NotNull ApiTarget target;
    private final @NotNull Map<TreeCode, OperationInvoker> operations;

    /** 校验目标编码、操作集合和选择规则；SINGLE 目标不得有 key，KEYED 目标必须有 key。 */
    public ApiBinding(@NotNull ApiDefinition definition, @NotNull ApiTarget target,
            @NotNull Map<TreeCode, OperationInvoker> operations) {
        this.definition = Objects.requireNonNull(definition);
        this.target = Objects.requireNonNull(target);
        this.operations = Map.copyOf(operations);
        if (!definition.code().equals(target.code()) || !definition.operations().keySet().equals(operations.keySet())) {
            throw new IllegalArgumentException("绑定与契约操作不一致");
        }
        if (definition.selection() == ExtensionSelection.SINGLE && target.key() != null
                || definition.selection() == ExtensionSelection.KEYED && target.key() == null) {
            throw new IllegalArgumentException("实现编码与选择规则不一致");
        }
    }

    /** 返回绑定契约。 */
    public @NotNull ApiDefinition definition() { return definition; }
    /** 返回调用目标。 */
    public @NotNull ApiTarget target() { return target; }
    /** 返回只读的直接调用分发器。 */
    public @NotNull Map<TreeCode, OperationInvoker> operations() { return operations; }
}
