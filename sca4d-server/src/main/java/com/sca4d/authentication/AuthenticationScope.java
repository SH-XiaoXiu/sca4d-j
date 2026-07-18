package com.sca4d.authentication;

import com.sca4d.common.auth.AccountScopeType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 匿名认证入口已经解析完成的 Account 作用域，只在后端保存租户主键。 */
public final class AuthenticationScope {

    private final AccountScopeType scopeType;
    private final Long scopeId;

    /**
     * 创建已经通过 GLOBAL/TENANT 组合校验的认证作用域。
     *
     * @param scopeType Account 作用域类型
     * @param scopeId TENANT 对应的租户主键；GLOBAL 为 {@code null}
     */
    public AuthenticationScope(@NotNull AccountScopeType scopeType, @Nullable Long scopeId) {
        if (scopeType == null
                || (scopeType == AccountScopeType.GLOBAL && scopeId != null)
                || (scopeType == AccountScopeType.TENANT && (scopeId == null || scopeId <= 0))) {
            throw new IllegalArgumentException("Account 作用域类型与主键不匹配");
        }
        this.scopeType = scopeType;
        this.scopeId = scopeId;
    }

    /** @return Account 作用域类型 */
    public @NotNull AccountScopeType getScopeType() {
        return scopeType;
    }

    /** @return TENANT 对应的租户主键；GLOBAL 为 {@code null} */
    public @Nullable Long getScopeId() {
        return scopeId;
    }
}
