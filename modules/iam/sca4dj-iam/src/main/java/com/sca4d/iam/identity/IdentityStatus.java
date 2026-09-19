package com.sca4d.iam.identity;

import com.sca4d.core.api.dictionary.Dictionary;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/** 表示平台身份当前是否可以参与登录和调用。 */
public enum IdentityStatus implements Dictionary {
    /** 当前身份可用。 */
    ACTIVE("ACTIVE", "启用"),
    /** 保留身份信息，但禁止继续使用。 */
    DISABLED("DISABLED", "停用");

    private final @NotNull String value;

    private final @NotNull String description;

    IdentityStatus(@NotNull String value, @NotNull String description) {
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

    /** @return Platform Account 状态字典键 */
    @Override
    @Contract(pure = true)
    public @NotNull String dictionaryKey() {
        return "iam.identity-status";
    }
}
