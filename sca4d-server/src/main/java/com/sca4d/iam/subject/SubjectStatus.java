package com.sca4d.iam.subject;

import com.sca4d.common.dictionary.CodeDictionary;
import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 登录身份状态。 */
@CodeDictionary(code = "iam.subject-status", nameKey = "dictionary.iam.subject-status")
public enum SubjectStatus implements BaseEnum {
    /** 可用。 */
    ACTIVE("ACTIVE", "可用"),
    /** 停用。 */
    DISABLED("DISABLED", "停用");

    private final String value;
    private final String description;

    SubjectStatus(@NotNull String value, @NotNull String description) {
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
