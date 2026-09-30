package com.sca4d.core.api.plugin;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 保存 App 公开契约及其可调用操作，不持有实际业务对象。 */
public final class ApiDefinition {
    private final @NotNull ApiKind kind;

    private final @NotNull ExtensionSelection selection;

    private final @NotNull TreeCode code;

    private final boolean required;

    private final @NotNull Map<TreeCode, ApiOperation> operations;

    /** 保存只读操作目录；操作编码重复时拒绝构造，required 表示启动前必须存在扩展实现。 */
    public ApiDefinition(
            @NotNull TreeCode code,
            @NotNull ApiKind kind,
            @NotNull ExtensionSelection selection,
            boolean required,
            @NotNull List<ApiOperation> operations) {
        this.code = Objects.requireNonNull(code);
        this.kind = Objects.requireNonNull(kind);
        this.selection = Objects.requireNonNull(selection);
        this.required = required;
        Map<TreeCode, ApiOperation> indexed = new LinkedHashMap<>();
        for (ApiOperation operation : operations) {
            if (indexed.putIfAbsent(operation.code(), operation) != null) {
                throw new IllegalArgumentException("公开操作编码重复: " + operation.code());
            }
        }
        this.operations = Map.copyOf(indexed);
    }

    /**
     * 契约编码。
     *
     * @return 契约编码
     */
    public @NotNull TreeCode code() {
        return code;
    }

    /**
     * 契约方向。
     *
     * @return 契约方向
     */
    public @NotNull ApiKind kind() {
        return kind;
    }

    /**
     * 扩展实现选择规则。
     *
     * @return 扩展实现选择规则
     */
    public @NotNull ExtensionSelection selection() {
        return selection;
    }

    /**
     * 启动前是否必须存在实现。
     *
     * @return 启动前是否必须存在实现
     */
    public boolean required() {
        return required;
    }

    /**
     * 只读操作目录。
     *
     * @return 只读操作目录
     */
    public @NotNull Map<TreeCode, ApiOperation> operations() {
        return operations;
    }
}
