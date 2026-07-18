package com.sca4d.iam.passkey.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.exception.InternalServiceException;
import com.sca4d.common.redis.RedisScriptDefinition;
import com.sca4d.common.redis.RedisScriptExecutor;
import com.sca4d.config.properties.Sca4djAuthenticationProperties;
import com.sca4d.iam.passkey.PasskeyRegistration;
import com.sca4d.iam.passkey.PasskeyRegistrationState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

/** 使用 Redis String 和 Lua CAS 保存一次性 Passkey registration。 */
@Repository
public class PasskeyRegistrationRepository {
    private static final RedisScriptDefinition<Long> CREATE_SCRIPT = new RedisScriptDefinition<>(
            "redis/authentication/flow/create.lua", Long.class);
    private static final RedisScriptDefinition<Long> CONSUME_SCRIPT = new RedisScriptDefinition<>(
            "redis/iam/passkey-registration/consume.lua", Long.class);
    private static final RedisScriptDefinition<String> GET_SCRIPT = new RedisScriptDefinition<>(
            "redis/get.lua", String.class);

    private final RedisScriptExecutor scripts;
    private final ObjectMapper objectMapper;
    private final Sca4djAuthenticationProperties properties;

    /**
     * @param scripts Redis Lua 脚本执行入口
     * @param objectMapper 项目 JSON 枚举和时间转换配置
     * @param properties Passkey registration key 前缀和有效期配置
     */
    public PasskeyRegistrationRepository(
            @NotNull RedisScriptExecutor scripts,
            @NotNull ObjectMapper objectMapper,
            @NotNull Sca4djAuthenticationProperties properties) {
        this.scripts = scripts;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /** 创建 UUID 对应的短期 registration；碰撞时失败且不覆盖原 challenge。 */
    public void create(@NotNull PasskeyRegistration registration, @NotNull Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Passkey registration TTL 必须大于 0");
        }
        String value;
        try {
            value = objectMapper.writeValueAsString(registration);
        } catch (JsonProcessingException exception) {
            throw new InternalServiceException("Passkey registration 序列化失败", exception);
        }
        Long created = scripts.execute(
                CREATE_SCRIPT,
                List.of(key(registration.getRegistrationId())),
                value,
                Long.toString(Math.max(1, ttl.toMillis())));
        if (!Long.valueOf(1L).equals(created)) {
            throw new InternalServiceException("Passkey registration UUID 已存在");
        }
    }

    /** @return registration 当前快照；不存在或 TTL 已过期时为 {@code null} */
    public @Nullable PasskeyRegistration find(@NotNull String registrationId) {
        String value = scripts.execute(GET_SCRIPT, List.of(key(registrationId)));
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, PasskeyRegistration.class);
        } catch (JsonProcessingException exception) {
            throw new InternalServiceException("Passkey registration 反序列化失败", exception);
        }
    }

    /** @return Redis 中仍是读取版本的 PENDING registration 时消费成功 */
    public boolean consume(
            @NotNull PasskeyRegistration registration, @NotNull Long expectedVersion) {
        String value;
        try {
            value = objectMapper.writeValueAsString(registration);
        } catch (JsonProcessingException exception) {
            throw new InternalServiceException("Passkey registration 序列化失败", exception);
        }
        Long consumed = scripts.execute(
                CONSUME_SCRIPT,
                List.of(key(registration.getRegistrationId())),
                expectedVersion.toString(),
                PasskeyRegistrationState.PENDING.value(),
                value);
        return Long.valueOf(1L).equals(consumed);
    }

    /** @return 当前部署隔离后的 Redis key */
    private @NotNull String key(@NotNull String registrationId) {
        return properties.getPasskey().getRegistrationKeyPrefix() + ":" + registrationId;
    }
}
