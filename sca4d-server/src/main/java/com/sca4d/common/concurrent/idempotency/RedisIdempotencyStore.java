package com.sca4d.common.concurrent.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.exception.InternalServiceException;
import com.sca4d.common.redis.RedisScriptDefinition;
import com.sca4d.common.redis.RedisScriptExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Redis 幂等记录存储。
 */
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final String ACQUIRED = "ACQUIRED";
    private static final RedisScriptDefinition<String> BEGIN_SCRIPT = new RedisScriptDefinition<>(
            "redis/idempotency/begin.lua", String.class);
    private static final RedisScriptDefinition<Long> SUCCEED_SCRIPT = new RedisScriptDefinition<>(
            "redis/idempotency/succeed.lua", Long.class);
    private static final RedisScriptDefinition<Long> RELEASE_SCRIPT = new RedisScriptDefinition<>(
            "redis/idempotency/release.lua", Long.class);

    private final RedisScriptExecutor scripts;
    private final ObjectMapper objectMapper;

    /**
     * 创建使用 Lua 所有权校验推进状态的 Redis 幂等存储。
     *
     * @param scripts Redis Lua 统一执行边界
     * @param objectMapper 幂等记录使用的全局 JSON 契约
     */
    public RedisIdempotencyStore(@NotNull RedisScriptExecutor scripts, @NotNull ObjectMapper objectMapper) {
        this.scripts = scripts;
        this.objectMapper = objectMapper;
    }

    @Override
    public @NotNull IdempotencyBeginResult begin(
            @NotNull String key,
            @NotNull String fingerprint,
            @NotNull Duration processingTtl,
            @NotNull Duration retryAfter) {
        String token = UUID.randomUUID().toString();
        IdempotencyRecord processing = IdempotencyRecord.processing(fingerprint, token);
        String result = scripts.execute(
                BEGIN_SCRIPT,
                List.of(key),
                write(processing),
                Long.toString(ttl(processingTtl).toMillis()),
                ACQUIRED);
        if (ACQUIRED.equals(result)) {
            return IdempotencyBeginResult.acquired(token);
        }

        IdempotencyRecord existing = read(result);
        if (existing == null) {
            return IdempotencyBeginResult.inProgress(retryAfter);
        }
        if (!Objects.equals(existing.getFingerprint(), fingerprint)) {
            return IdempotencyBeginResult.conflict();
        }
        if (existing.getStatus() == IdempotencyStatus.SUCCEEDED) {
            return IdempotencyBeginResult.replay(existing);
        }
        return IdempotencyBeginResult.inProgress(retryAfter);
    }

    @Override
    public boolean succeed(
            @NotNull String key,
            @NotNull String token,
            @NotNull String fingerprint,
            @Nullable String responseJson,
            @NotNull String responseType,
            @NotNull Duration successTtl) {
        IdempotencyRecord succeeded = IdempotencyRecord.succeeded(fingerprint, responseJson, responseType);
        Long updated = scripts.execute(
                SUCCEED_SCRIPT,
                List.of(key),
                token,
                write(succeeded),
                String.valueOf(ttl(successTtl).toMillis()));
        return Long.valueOf(1L).equals(updated);
    }

    @Override
    public boolean release(@NotNull String key, @NotNull String token) {
        Long deleted = scripts.execute(RELEASE_SCRIPT, List.of(key), token);
        return Long.valueOf(1L).equals(deleted);
    }

    /**
     * 将正数 TTL 规范到 Redis 毫秒精度。
     *
     * @param ttl 处理中租约或成功记录保留时间
     * @return 至少一毫秒的 Redis TTL
     * @throws IllegalArgumentException TTL 为零或负数时抛出
     */
    private @NotNull Duration ttl(@NotNull Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("幂等记录 TTL 必须大于 0");
        }
        return Duration.ofMillis(Math.max(1, ttl.toMillis()));
    }

    /**
     * 使用全局 JSON 契约序列化 Redis 幂等记录。
     *
     * @param record 处理中所有权或成功响应记录
     * @return Redis String value
     * @throws InternalServiceException 记录无法序列化时抛出
     */
    private @NotNull String write(@NotNull IdempotencyRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException exception) {
            throw new InternalServiceException("幂等记录序列化失败", exception);
        }
    }

    /**
     * 读取 Redis 幂等记录并拒绝无法解释的损坏数据。
     *
     * @param value Redis String value，key 不存在时为 null
     * @return key 不存在或值为空白时返回 null，否则返回结构化记录
     * @throws InternalServiceException 已存在值不符合当前幂等记录契约时抛出
     */
    private @Nullable IdempotencyRecord read(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, IdempotencyRecord.class);
        } catch (JsonProcessingException exception) {
            throw new InternalServiceException("幂等记录反序列化失败", exception);
        }
    }
}
