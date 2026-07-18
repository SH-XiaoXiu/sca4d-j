package com.sca4d.tenant;

import com.sca4d.common.dictionary.CodeDictionary;
import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 租户成员关系状态。 */
@CodeDictionary(code = "iam.membership-status", nameKey = "dictionary.iam.membership-status")
public enum MembershipStatus implements BaseEnum {
    /** 成员可以进入租户工作空间。 */
    ACTIVE("ACTIVE", "可用"),
    /** 成员保留历史关系但不能进入租户工作空间。 */
    DISABLED("DISABLED", "停用");

    private final String value;
    private final String description;

    MembershipStatus(@NotNull String value, @NotNull String description) {
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
