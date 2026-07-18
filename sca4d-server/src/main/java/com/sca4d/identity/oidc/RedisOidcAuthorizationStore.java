package com.sca4d.identity.oidc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.exception.InternalServiceException;
import com.sca4d.common.redis.RedisScriptDefinition;
import com.sca4d.common.redis.RedisScriptExecutor;
import com.sca4d.config.properties.Sca4djIdentityProviderProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/** 使用 Redis String 与 Lua 保存并单次消费 OIDC 授权请求。 */
@Component
public class RedisOidcAuthorizationStore implements OidcAuthorizationStore {

    private static final RedisScriptDefinition<Long> CREATE_SCRIPT = new RedisScriptDefinition<>(
            "redis/identity/oidc-authorization/create.lua", Long.class);
    private static final RedisScriptDefinition<String> CONSUME_SCRIPT = new RedisScriptDefinition<>(
            "redis/identity/oidc-authorization/consume.lua", String.class);

    private final RedisScriptExecutor scripts;
    private final ObjectMapper objectMapper;
    private final Sca4djIdentityProviderProperties properties;

    /**
     * @param scripts Redis Lua 执行入口
     * @param objectMapper 项目 JSON 配置
     * @param properties OIDC 授权请求 key 前缀
     */
    public RedisOidcAuthorizationStore(
            @NotNull RedisScriptExecutor scripts,
            @NotNull ObjectMapper objectMapper,
            @NotNull Sca4djIdentityProviderProperties properties) {
        this.scripts = scripts;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public boolean create(@NotNull OidcAuthorization authorization, @NotNull Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("OIDC 授权请求 TTL 必须大于 0");
        }
        String value;
        try {
            value = objectMapper.writeValueAsString(authorization);
        } catch (JsonProcessingException exception) {
            throw new InternalServiceException("OIDC 授权请求序列化失败", exception);
        }
        Long created = scripts.execute(
                CREATE_SCRIPT,
                List.of(properties.getOidc().getAuthorizationKeyPrefix()
                        + ":"
                        + authorization.getAuthorizationId()),
                value,
                Long.toString(Math.max(1, ttl.toMillis())));
        return Long.valueOf(1L).equals(created);
    }

    @Override
    public @Nullable OidcAuthorization consume(
            @NotNull String authorizationId, @NotNull String browserHash) {
        String value = scripts.execute(
                CONSUME_SCRIPT,
                List.of(properties.getOidc().getAuthorizationKeyPrefix() + ":" + authorizationId),
                browserHash);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, OidcAuthorization.class);
        } catch (JsonProcessingException exception) {
            throw new InternalServiceException("OIDC 授权请求反序列化失败", exception);
        }
    }
}
