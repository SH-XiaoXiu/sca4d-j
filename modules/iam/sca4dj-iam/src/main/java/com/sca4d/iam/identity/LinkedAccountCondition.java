package com.sca4d.iam.identity;

import com.sca4d.core.api.identity.RealmKey;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 表示 Principal 查询要求存在的可用 Account 关系。 */
public final class LinkedAccountCondition {

    private final @NotNull RealmKey realm;

    private LinkedAccountCondition(@NotNull RealmKey realm) {
        this.realm = Objects.requireNonNull(realm, "认证域不能为空");
    }

    /**
     * 要求 Principal 至少关联一个指定认证域中仍启用且所属 Subject 仍启用的 Account。
     *
     * @param realm Account 所属认证域
     * @return 可传给 {@link PrincipalQuery.Builder#linkedAccount(LinkedAccountCondition)} 的条件
     */
    @Contract(value = "_ -> new", pure = true)
    @CheckReturnValue
    public static @NotNull LinkedAccountCondition activeIn(@NotNull RealmKey realm) {
        return new LinkedAccountCondition(realm);
    }

    /** @return Account 所属认证域 */
    @Contract(pure = true)
    @NotNull
    RealmKey realm() {
        return realm;
    }
}
