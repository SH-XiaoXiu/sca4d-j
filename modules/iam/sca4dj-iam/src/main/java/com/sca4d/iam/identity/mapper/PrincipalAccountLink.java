package com.sca4d.iam.identity.mapper;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 批量查询 Principal 与 Account 关系的内部行。 */
@SuppressWarnings("ClassCanBeRecord")
public final class PrincipalAccountLink {
    private final @NotNull Long principalId;

    private final @NotNull Long accountId;

    /**
     * 创建关系查询结果。
     *
     * @param principalId 业务主体标识
     * @param accountId 账号标识
     */
    public PrincipalAccountLink(@NotNull Long principalId, @NotNull Long accountId) {
        this.principalId = Objects.requireNonNull(principalId, "业务主体标识不能为空");
        this.accountId = Objects.requireNonNull(accountId, "账号标识不能为空");
    }

    /**
     * 返回业务主体标识。
     *
     * @return 业务主体标识
     */
    @Contract(pure = true)
    public @NotNull Long principalId() {
        return principalId;
    }

    /**
     * 返回账号标识。
     *
     * @return 账号标识
     */
    @Contract(pure = true)
    public @NotNull Long accountId() {
        return accountId;
    }
}
