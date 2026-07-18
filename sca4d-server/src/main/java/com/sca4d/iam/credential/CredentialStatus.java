package com.sca4d.iam.credential;

import com.sca4d.common.dictionary.CodeDictionary;
import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 登录凭证状态。 */
@CodeDictionary(code = "iam.credential-status", nameKey = "dictionary.iam.credential-status")
public enum CredentialStatus implements BaseEnum {
    /** 可用。 */
    ACTIVE("ACTIVE", "可用"),
    /** 停用。 */
    DISABLED("DISABLED", "停用");

    private final String value;
    private final String description;

    CredentialStatus(@NotNull String value, @NotNull String description) {
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
