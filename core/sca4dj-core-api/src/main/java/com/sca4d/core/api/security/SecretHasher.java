package com.sca4d.core.api.security;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

/** 为不需要恢复原文的秘密值生成单向摘要，并校验原始值与摘要是否匹配。 */
public interface SecretHasher {

    /**
     * 为原始秘密值生成包含算法、参数和盐值信息的单向摘要。
     *
     * @param secret 只在当前调用中使用的原始秘密值
     * @return 可由 {@link #matches(String, String)} 校验的完整摘要
     */
    @CheckReturnValue
    @NotNull
    String hash(@NotNull String secret);

    /**
     * 校验原始秘密值是否与已保存摘要匹配。
     *
     * @param secret 待校验的原始秘密值
     * @param secretHash 先前由兼容实现生成的完整摘要
     * @return 原始值与摘要匹配时返回 {@code true}
     */
    boolean matches(@NotNull String secret, @NotNull String secretHash);
}
