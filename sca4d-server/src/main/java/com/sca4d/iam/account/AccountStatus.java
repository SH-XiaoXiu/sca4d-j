package com.sca4d.iam.account;

import com.sca4d.common.dictionary.CodeDictionary;
import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** Account 登录状态。 */
@CodeDictionary(code = "iam.account-status", nameKey = "dictionary.iam.account-status")
public enum AccountStatus implements BaseEnum {
    /** 允许使用已启用认证方式登录。 */
    ACTIVE("ACTIVE", "启用"),
    /** 禁止创建新 Session。 */
    DISABLED("DISABLED", "停用");

    private final String value;
    private final String description;

    AccountStatus(@NotNull String value, @NotNull String description) {
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
