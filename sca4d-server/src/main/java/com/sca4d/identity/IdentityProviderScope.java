package com.sca4d.identity;

import com.sca4d.common.auth.AccountScopeType;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** IdentityProvider 和目标 Account 必须共同使用的 GLOBAL 或 TENANT 作用域。 */
@Getter
public final class IdentityProviderScope {

    private final AccountScopeType scopeType;
    private final Long scopeId;

    /** 创建平台全局身份源作用域。 */
    public static @NotNull IdentityProviderScope global() {
        return new IdentityProviderScope(AccountScopeType.GLOBAL, null);
    }

    /** 创建指定租户的身份源作用域。 */
    public static @NotNull IdentityProviderScope tenant(@NotNull Long tenantId) {
        return new IdentityProviderScope(AccountScopeType.TENANT, tenantId);
    }

    /**
     * 创建数据库读取结果对应的作用域，并拒绝 GLOBAL/TENANT 非法组合。
     *
     * @throws IllegalArgumentException GLOBAL 携带主键或 TENANT 缺少正数主键时抛出
     */
    public IdentityProviderScope(@NotNull AccountScopeType scopeType, @Nullable Long scopeId) {
        if (scopeType == AccountScopeType.GLOBAL && scopeId != null) {
            throw new IllegalArgumentException("GLOBAL IdentityProvider scopeId 必须为空");
        }
        if (scopeType == AccountScopeType.TENANT && (scopeId == null || scopeId <= 0)) {
            throw new IllegalArgumentException("TENANT IdentityProvider scopeId 必须为正数");
        }
        this.scopeType = scopeType;
        this.scopeId = scopeId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (!(other instanceof IdentityProviderScope that))
            return false;
        return scopeType == that.scopeType && Objects.equals(scopeId, that.scopeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scopeType, scopeId);
    }
}
