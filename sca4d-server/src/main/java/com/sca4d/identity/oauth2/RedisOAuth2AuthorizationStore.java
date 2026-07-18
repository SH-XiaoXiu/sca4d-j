package com.sca4d.identity.oauth2;

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

/** 使用 Redis String 与资源目录 Lua 保存并单次消费 OAuth2 授权请求。 */
@Component
public class RedisOAuth2AuthorizationStore implements OAuth2AuthorizationStore {

    private static final RedisScriptDefinition<Long> CREATE_SCRIPT = new RedisScriptDefinition<>(
            "redis/identity/oauth2-authorization/create.lua", Long.class);
    private static final RedisScriptDefinition<String> CONSUME_SCRIPT = new RedisScriptDefinition<>(
            "redis/identity/oauth2-authorization/consume.lua", String.class);

    private final RedisScriptExecutor scripts;
    private final ObjectMapper objectMapper;
    private final Sca4djIdentityProviderProperties properties;

    public RedisOAuth2AuthorizationStore(
            @NotNull RedisScriptExecutor scripts,
            @NotNull ObjectMapper objectMapper,
            @NotNull Sca4djIdentityProviderProperties properties) {
        this.scripts = scripts;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public boolean create(@NotNull OAuth2Authorization authorization, @NotNull Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("OAuth2 授权请求 TTL 必须大于 0");
        }
        String value;
        try {
            value = objectMapper.writeValueAsString(authorization);
        } catch (JsonProcessingException exception) {
            throw new InternalServiceException("OAuth2 授权请求序列化失败", exception);
        }
        Long created = scripts.execute(
                CREATE_SCRIPT,
                List.of(properties.getOauth2().getAuthorizationKeyPrefix()
                        + ":"
                        + authorization.getAuthorizationId()),
                value,
                Long.toString(Math.max(1, ttl.toMillis())));
        return Long.valueOf(1L).equals(created);
    }

    @Override
    public @Nullable OAuth2Authorization consume(
            @NotNull String authorizationId, @NotNull String browserHash) {
        String value = scripts.execute(
                CONSUME_SCRIPT,
                List.of(properties.getOauth2().getAuthorizationKeyPrefix() + ":" + authorizationId),
                browserHash);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, OAuth2Authorization.class);
        } catch (JsonProcessingException exception) {
            throw new InternalServiceException("OAuth2 授权请求反序列化失败", exception);
        }
    }
}
