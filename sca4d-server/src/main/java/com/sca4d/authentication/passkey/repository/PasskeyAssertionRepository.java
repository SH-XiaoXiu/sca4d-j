package com.sca4d.authentication.passkey.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.authentication.passkey.PasskeyAssertion;
import com.sca4d.authentication.passkey.PasskeyAssertionState;
import com.sca4d.common.exception.InternalServiceException;
import com.sca4d.common.redis.RedisScriptDefinition;
import com.sca4d.common.redis.RedisScriptExecutor;
import com.sca4d.config.properties.Sca4djAuthenticationProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

/** 使用 Redis String 和 Lua CAS 保存一次性 Passkey assertion。 */
@Repository
public class PasskeyAssertionRepository {
    private static final RedisScriptDefinition<Long> CREATE_SCRIPT = new RedisScriptDefinition<>(
            "redis/authentication/flow/create.lua", Long.class);
    private static final RedisScriptDefinition<Long> CONSUME_SCRIPT = new RedisScriptDefinition<>(
            "redis/authentication/passkey-assertion/consume.lua", Long.class);
    private static final RedisScriptDefinition<String> GET_SCRIPT = new RedisScriptDefinition<>(
            "redis/get.lua", String.class);

    private final RedisScriptExecutor scripts;
    private final ObjectMapper objectMapper;
    private final Sca4djAuthenticationProperties properties;

    /**
     * @param scripts Redis Lua 脚本执行入口
     * @param objectMapper 项目 JSON 枚举和时间转换配置
     * @param properties Passkey assertion key 前缀和有效期配置
     */
    public PasskeyAssertionRepository(
            @NotNull RedisScriptExecutor scripts,
            @NotNull ObjectMapper objectMapper,
            @NotNull Sca4djAuthenticationProperties properties) {
        this.scripts = scripts;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /** 创建 UUID 对应的短期 assertion；碰撞时失败且不覆盖原 challenge。 */
    public void create(@NotNull PasskeyAssertion assertion, @NotNull Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Passkey assertion TTL 必须大于 0");
        }
        String value;
        try {
            value = objectMapper.writeValueAsString(assertion);
        } catch (JsonProcessingException exception) {
            throw new InternalServiceException("Passkey assertion 序列化失败", exception);
        }
        Long created = scripts.execute(
                CREATE_SCRIPT,
                List.of(key(assertion.getAssertionId())),
                value,
                Long.toString(Math.max(1, ttl.toMillis())));
        if (!Long.valueOf(1L).equals(created)) {
            throw new InternalServiceException("Passkey assertion UUID 已存在");
        }
    }

    /** @return assertion 当前快照；不存在或 TTL 已过期时为 {@code null} */
    public @Nullable PasskeyAssertion find(@NotNull String assertionId) {
        String value = scripts.execute(GET_SCRIPT, List.of(key(assertionId)));
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, PasskeyAssertion.class);
        } catch (JsonProcessingException exception) {
            throw new InternalServiceException("Passkey assertion 反序列化失败", exception);
        }
    }

    /** @return Redis 中仍是读取版本的 PENDING assertion 时消费成功 */
    public boolean consume(@NotNull PasskeyAssertion assertion, @NotNull Long expectedVersion) {
        String value;
        try {
            value = objectMapper.writeValueAsString(assertion);
        } catch (JsonProcessingException exception) {
            throw new InternalServiceException("Passkey assertion 序列化失败", exception);
        }
        Long consumed = scripts.execute(
                CONSUME_SCRIPT,
                List.of(key(assertion.getAssertionId())),
                expectedVersion.toString(),
                PasskeyAssertionState.PENDING.value(),
                value);
        return Long.valueOf(1L).equals(consumed);
    }

    /** @return 当前部署隔离后的 Redis key */
    private @NotNull String key(@NotNull String assertionId) {
        return properties.getPasskey().getAssertionKeyPrefix() + ":" + assertionId;
    }
}
