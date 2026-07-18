package com.sca4d.iam.totp.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.exception.InternalServiceException;
import com.sca4d.common.redis.RedisScriptDefinition;
import com.sca4d.common.redis.RedisScriptExecutor;
import com.sca4d.config.properties.Sca4djAuthenticationProperties;
import com.sca4d.iam.totp.TotpEnrollment;
import com.sca4d.iam.totp.TotpEnrollmentState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

/** 使用 Redis String 和 Lua CAS 保存一次性 TOTP enrollment。 */
@Repository
public class TotpEnrollmentRepository {
    private static final RedisScriptDefinition<Long> CREATE_SCRIPT = new RedisScriptDefinition<>(
            "redis/authentication/flow/create.lua", Long.class);
    private static final RedisScriptDefinition<Long> CONSUME_SCRIPT = new RedisScriptDefinition<>(
            "redis/iam/totp-enrollment/consume.lua", Long.class);
    private static final RedisScriptDefinition<String> GET_SCRIPT = new RedisScriptDefinition<>(
            "redis/get.lua", String.class);

    private final RedisScriptExecutor scripts;
    private final ObjectMapper objectMapper;
    private final Sca4djAuthenticationProperties properties;

    /**
     * @param scripts Redis Lua 脚本执行入口
     * @param objectMapper 项目 JSON 枚举和时间转换配置
     * @param properties enrollment key 前缀和有效期配置
     */
    public TotpEnrollmentRepository(
            @NotNull RedisScriptExecutor scripts,
            @NotNull ObjectMapper objectMapper,
            @NotNull Sca4djAuthenticationProperties properties) {
        this.scripts = scripts;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /** 创建 UUID 对应的短期 enrollment；碰撞时失败而不覆盖已有密钥。 */
    public void create(@NotNull TotpEnrollment enrollment, @NotNull Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("TOTP enrollment TTL 必须大于 0");
        }
        String value;
        try {
            value = objectMapper.writeValueAsString(enrollment);
        } catch (JsonProcessingException exception) {
            throw new InternalServiceException("TOTP enrollment 序列化失败", exception);
        }
        Long created = scripts.execute(
                CREATE_SCRIPT,
                List.of(key(enrollment.getEnrollmentId())),
                value,
                Long.toString(Math.max(1, ttl.toMillis())));
        if (!Long.valueOf(1L).equals(created)) {
            throw new InternalServiceException("TOTP enrollment UUID 已存在");
        }
    }

    /** @return enrollment 当前快照；不存在或 TTL 已过期时为 {@code null} */
    public @Nullable TotpEnrollment find(@NotNull String enrollmentId) {
        String value = scripts.execute(GET_SCRIPT, List.of(key(enrollmentId)));
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, TotpEnrollment.class);
        } catch (JsonProcessingException exception) {
            throw new InternalServiceException("TOTP enrollment 反序列化失败", exception);
        }
    }

    /**
     * 仅在 Redis 中仍为调用方读取的 PENDING 版本时写入 CONSUMED。
     *
     * @return 跨节点并发激活中当前调用首次消费成功时为 {@code true}
     */
    public boolean consume(@NotNull TotpEnrollment enrollment, @NotNull Long expectedVersion) {
        String value;
        try {
            value = objectMapper.writeValueAsString(enrollment);
        } catch (JsonProcessingException exception) {
            throw new InternalServiceException("TOTP enrollment 序列化失败", exception);
        }
        Long consumed = scripts.execute(
                CONSUME_SCRIPT,
                List.of(key(enrollment.getEnrollmentId())),
                expectedVersion.toString(),
                TotpEnrollmentState.PENDING.value(),
                value);
        return Long.valueOf(1L).equals(consumed);
    }

    /** @return 当前部署隔离后的 Redis key */
    private @NotNull String key(@NotNull String enrollmentId) {
        return properties.getTotp().getEnrollmentKeyPrefix() + ":" + enrollmentId;
    }
}
