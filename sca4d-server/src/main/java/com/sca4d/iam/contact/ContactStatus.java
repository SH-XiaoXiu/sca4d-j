package com.sca4d.iam.contact;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** Contact 是否可以显示和参与关联判断。 */
public enum ContactStatus implements BaseEnum {
    /** Contact 可以正常使用。 */
    ACTIVE("ACTIVE", "启用"),
    /** Contact 保留但不参与登录和自动关联。 */
    DISABLED("DISABLED", "停用");

    private final String value;
    private final String description;

    ContactStatus(@NotNull String value, @NotNull String description) {
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
