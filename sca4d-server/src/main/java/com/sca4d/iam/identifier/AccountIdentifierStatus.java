package com.sca4d.iam.identifier;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** AccountIdentifier 是否可以参与登录解析。 */
public enum AccountIdentifierStatus implements BaseEnum {
    /** 可以解析到所属 Account。 */
    ACTIVE("ACTIVE", "启用"),
    /** 保留记录但禁止用于登录。 */
    DISABLED("DISABLED", "停用");

    private final String value;
    private final String description;

    AccountIdentifierStatus(@NotNull String value, @NotNull String description) {
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
