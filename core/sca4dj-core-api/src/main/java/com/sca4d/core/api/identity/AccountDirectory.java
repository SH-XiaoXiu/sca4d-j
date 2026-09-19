package com.sca4d.core.api.identity;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * 按开放登录标识查找可用 Account，并校验指定 Account 是否仍然可用。
 */
public interface AccountDirectory {

    /**
     * 在指定认证域中按原始登录标识查找完整可用身份链对应的 Account。
     *
     * @param realm 认证域
     * @param type 登录标识类型
     * @param value 原始登录标识值，由实现负责按类型规范化
     * @return 身份链完整可用时返回 Account，否则返回空结果
     */
    @CheckReturnValue
    @NotNull
    Optional<Account> resolve(
            @NotNull RealmKey realm,
            @NotNull AccountIdentifierType type,
            @NotNull String value);

    /**
     * 读取必须存在且启用的 Account。
     *
     * @param accountId Account 标识
     * @return 启用的 Account
     */
    @CheckReturnValue
    @NotNull
    Account activeById(long accountId);
}
