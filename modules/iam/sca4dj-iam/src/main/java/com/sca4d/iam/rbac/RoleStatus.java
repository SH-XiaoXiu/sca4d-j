package com.sca4d.iam.rbac;

import com.sca4d.core.api.dictionary.Dictionary;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/** 表示角色是否贡献已分配的 Operation。 */
public enum RoleStatus implements Dictionary {
    /** 角色向关联 Principal 贡献 Operation。 */
    ACTIVE("ACTIVE", "启用"),
    /** 保留角色和关系，但不向 Principal 贡献 Operation。 */
    DISABLED("DISABLED", "停用");

    private final @NotNull String value;

    private final @NotNull String description;

    RoleStatus(@NotNull String value, @NotNull String description) {
        this.value = value;
        this.description = description;
    }

    @Override
    @Contract(pure = true)
    public @NotNull String value() {
        return value;
    }

    @Override
    @Contract(pure = true)
    public @NotNull String description() {
        return description;
    }

    /** @return Platform Role 状态字典键 */
    @Override
    @Contract(pure = true)
    public @NotNull String dictionaryKey() {
        return "iam.role-status";
    }
}
