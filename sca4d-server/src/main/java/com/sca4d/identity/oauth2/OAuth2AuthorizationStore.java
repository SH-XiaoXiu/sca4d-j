package com.sca4d.identity.oauth2;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/** 创建并单次消费 OAuth2 授权请求；消费会删除 Redis 中的 state。 */
public interface OAuth2AuthorizationStore {

    /** @return state 尚不存在且保存成功时返回 true */
    boolean create(@NotNull OAuth2Authorization authorization, @NotNull Duration ttl);

    /** @return 首次消费时返回授权请求；不存在、过期、浏览器不匹配或已消费时返回 null */
    @Nullable
    OAuth2Authorization consume(
            @NotNull String authorizationId, @NotNull String browserHash);
}
