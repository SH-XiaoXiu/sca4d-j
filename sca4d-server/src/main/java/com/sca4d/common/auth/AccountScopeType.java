package com.sca4d.common.auth;

import com.sca4d.common.dictionary.CodeDictionary;
import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 登录账号的身份作用域类型。 */
@CodeDictionary(code = "iam.account-scope-type", nameKey = "dictionary.iam.account-scope-type")
public enum AccountScopeType implements BaseEnum {
    /** 平台全局账号。 */
    GLOBAL("GLOBAL", "全局"),
    /** 只在一个租户中解析登录标识的账号。 */
    TENANT("TENANT", "租户");

    private final String value;
    private final String description;

    AccountScopeType(@NotNull String value, @NotNull String description) {
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
