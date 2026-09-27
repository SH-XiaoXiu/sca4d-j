package com.sca4d.iam.rbac;

import com.sca4d.core.api.permission.Operation;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/** 保存角色以及已经展开为具体 ACTION 的 Operation。 */
@SuppressWarnings("ClassCanBeRecord")
public class RolePermissions {

    private final @NotNull Role role;

    private final @NotNull @Unmodifiable List<@NotNull Operation> operations;

    /**
     * 创建角色 Operation 结果。
     *
     * @param role 当前角色
     * @param operations 已展开的具体 ACTION
     */
    public RolePermissions(@NotNull Role role, @NotNull List<@NotNull Operation> operations) {
        this.role = Objects.requireNonNull(role, "角色不能为空");
        this.operations = List.copyOf(operations);
    }

    /**
     * 返回当前角色。
     *
     * @return 当前角色
     */
    @Contract(pure = true)
    public @NotNull Role role() {
        return role;
    }

    /**
     * 返回展开后的具体 ACTION。
     *
     * @return 不包含内部选择符的不可修改列表
     */
    @Contract(pure = true)
    public @NotNull @Unmodifiable List<@NotNull Operation> operations() {
        return operations;
    }
}
