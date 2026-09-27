package com.sca4d.core.api.identity;

import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.Code;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

/** 使用稳定 Code 标识账号登录标识的种类，并保留独立领域类型。 */
public final class AccountIdentifierType extends Code {
    /** 用户名标识的稳定编码。 */
    public static final @NotNull AccountIdentifierType USERNAME = new AccountIdentifierType("USERNAME");

    /** 从代码声明或序列化边界解析稳定类型编码。 */
    public AccountIdentifierType(@NotNull String text) {
        this(TreeCode.of(text));
    }

    /** 复用已校验的稳定节点，拒绝通配符和超过数据库限制的名称。 */
    public AccountIdentifierType(@NotNull Code code) {
        super(code);
        if (nodes().contains("*") || format(NameFormat.DOT).length() > 128) {
            throw new IllegalArgumentException("AccountIdentifierType 必须是最多 128 字符的固定编码");
        }
    }
}
