package com.sca4d.iam.tenant;

import com.sca4d.common.dictionary.CodeDictionary;
import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 租户类型。 */
@CodeDictionary(code = "iam.tenant-type", nameKey = "dictionary.iam.tenant-type")
public enum TenantType implements BaseEnum {
    /** 默认租户。 */
    DEFAULT("DEFAULT", "默认租户");

    private final String value;
    private final String description;

    TenantType(@NotNull String value, @NotNull String description) {
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
