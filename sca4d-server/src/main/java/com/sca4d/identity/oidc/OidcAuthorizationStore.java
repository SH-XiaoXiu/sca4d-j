package com.sca4d.identity.oidc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/** 创建并单次消费 OIDC 授权请求；消费会删除 Redis 中的 state。 */
public interface OidcAuthorizationStore {

    /**
     * 使用 state 作为唯一键保存 OIDC 授权请求。
     *
     * @return state 尚不存在且保存成功时返回 true
     */
    boolean create(@NotNull OidcAuthorization authorization, @NotNull Duration ttl);

    /**
     * 原子读取并删除指定 state。
     *
     * @return 首次消费时返回授权请求；不存在、过期或已经消费时返回 null
     */
    @Nullable
    OidcAuthorization consume(
            @NotNull String authorizationId, @NotNull String browserHash);
}
