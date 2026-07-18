package com.sca4d.iam.contact;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

import java.text.Normalizer;
import java.util.Locale;

/** Subject 联系资料类型。 */
public enum ContactType implements BaseEnum {
    /** 首版可通过邮件验证码或可信 IdentityProvider 确认。 */
    EMAIL("EMAIL", "邮箱"),
    /** 仅保存资料，首版不提供短信验证或手机号登录。 */
    PHONE("PHONE", "电话");

    private final String value;
    private final String description;

    ContactType(@NotNull String value, @NotNull String description) {
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

    /** 将联系资料原值转换为查重和关联使用的固定规范值。 */
    public @NotNull String normalize(@NotNull String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        if (normalized.length() > 256) {
            throw new IllegalArgumentException("Contact 长度不能超过 256 个字符");
        }
        return normalized;
    }
}
