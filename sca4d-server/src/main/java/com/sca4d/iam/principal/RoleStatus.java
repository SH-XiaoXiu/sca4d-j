package com.sca4d.iam.principal;

import com.sca4d.common.dictionary.CodeDictionary;
import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** IAM 角色当前是否可以参与权限计算。 */
@CodeDictionary(code = "iam.role-status", nameKey = "dictionary.iam.role-status")
public enum RoleStatus implements BaseEnum {
    /** 角色及其权限关系可以参与 Session 授权聚合。 */
    ACTIVE("ACTIVE", "可用"),
    /** 保留角色和绑定关系，但不参与新 Session 授权聚合。 */
    DISABLED("DISABLED", "停用");

    private final String value;
    private final String description;

    RoleStatus(@NotNull String value, @NotNull String description) {
        this.value = value;
        this.description = description;
    }

    @Override
    public @NotNull String value() {
        return value;
    }

    @Override
    public @NotNull String description() {
        return description;
    }
}
