package com.sca4d.identity.oauth2;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** OAuth2 Profile 映射可以产生的标准身份字段。 */
public enum OAuth2ProfileField implements BaseEnum {
    SUBJECT("SUBJECT", "外部主体"),
    DISPLAY_NAME("DISPLAY_NAME", "显示名称"),
    EMAIL("EMAIL", "邮箱"),
    EMAIL_VERIFIED("EMAIL_VERIFIED", "邮箱验证状态"),
    AVATAR("AVATAR", "头像");

    private final String value;
    private final String description;

    OAuth2ProfileField(@NotNull String value, @NotNull String description) {
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
