package com.sca4d.iam.identifier;

import com.sca4d.common.enums.BaseEnum;
import com.sca4d.common.exception.BizException;
import com.sca4d.iam.error.IamErrorCode;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.Locale;

/** Account 在身份作用域中可用于登录解析的标识类型。 */
public enum AccountIdentifierType implements BaseEnum {
    /** 用户自行选择或管理员分配的登录名。 */
    USERNAME("USERNAME", "用户名"),
    /** 指向已验证 EMAIL Contact 的登录邮箱。 */
    EMAIL("EMAIL", "邮箱");

    private final String value;
    private final String description;

    AccountIdentifierType(@NotNull String value, @NotNull String description) {
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

    /** 将登录输入转换为作用域解析和唯一索引使用的固定规范值。 */
    public @NotNull String normalize(@NotNull String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(IamErrorCode.IDENTIFIER_REQUIRED);
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        if (normalized.length() > 128
                || (this == USERNAME
                        && (normalized.indexOf(':') >= 0
                                || normalized.codePoints().anyMatch(Character::isISOControl)))) {
            throw new BizException(IamErrorCode.IDENTIFIER_INVALID);
        }
        return normalized;
    }
}
