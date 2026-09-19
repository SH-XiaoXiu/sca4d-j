package com.sca4d.iam.identity;

import com.sca4d.core.api.identity.AccountIdentifierType;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

/** 把一种登录标识转换成查找和唯一约束使用的稳定值。 */
public interface AccountIdentifierNormalizer {
    /**
     * 返回当前规范化器负责的标识类型。
     *
     * @return 标识类型
     */
    @CheckReturnValue
    @NotNull
    AccountIdentifierType type();

    /**
     * 将原始输入转换成稳定的查找值。
     *
     * @param rawValue 调用方提供的原始值
     * @return 用于查找和唯一约束的非空白值，长度不能超过 320 个字符
     */
    @CheckReturnValue
    @NotNull
    String normalize(@NotNull String rawValue);
}
