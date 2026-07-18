package com.sca4d.common.auth;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 当前会话完成认证的登录账号。 */
public class Account {

    private Long accountId;
    private Long subjectId;
    private AccountScopeType scopeType;
    private Long scopeId;
    private boolean secondFactorRequired;

    public Account() {
    }

    /**
     * 创建登录账号运行时对象。
     *
     * @param accountId Account 主键
     * @param subjectId 所属 Subject 主键
     * @param scopeType 登录标识的解析作用域
     * @param scopeId TENANT 账号所属租户；GLOBAL 账号为 null
     */
    public Account(
            @NotNull Long accountId,
            @NotNull Long subjectId,
            @NotNull AccountScopeType scopeType,
            @Nullable Long scopeId) {
        this(accountId, subjectId, scopeType, scopeId, false);
    }

    /**
     * 创建包含当前第二因子策略的登录账号运行时对象。
     *
     * @param accountId Account 主键
     * @param subjectId 所属 Subject 主键
     * @param scopeType 登录标识的解析作用域
     * @param scopeId TENANT 账号所属租户；GLOBAL 账号为 null
     * @param secondFactorRequired 创建 Session 前是否必须完成独立第二认证因素
     */
    public Account(
            @NotNull Long accountId,
            @NotNull Long subjectId,
            @NotNull AccountScopeType scopeType,
            @Nullable Long scopeId,
            boolean secondFactorRequired) {
        this.accountId = accountId;
        this.subjectId = subjectId;
        this.scopeType = scopeType;
        this.scopeId = scopeId;
        this.secondFactorRequired = secondFactorRequired;
    }

    public @NotNull Long getAccountId() {
        return accountId;
    }

    public void setAccountId(@NotNull Long accountId) {
        this.accountId = accountId;
    }

    public @NotNull Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(@NotNull Long subjectId) {
        this.subjectId = subjectId;
    }

    public @NotNull AccountScopeType getScopeType() {
        return scopeType;
    }

    public void setScopeType(@NotNull AccountScopeType scopeType) {
        this.scopeType = scopeType;
    }

    public @Nullable Long getScopeId() {
        return scopeId;
    }

    public void setScopeId(@Nullable Long scopeId) {
        this.scopeId = scopeId;
    }

    public boolean isSecondFactorRequired() {
        return secondFactorRequired;
    }

    public void setSecondFactorRequired(boolean secondFactorRequired) {
        this.secondFactorRequired = secondFactorRequired;
    }
}
