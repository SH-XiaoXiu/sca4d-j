package com.sca4d.authentication.email.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.authentication.email.EmailCodeChallenge;
import com.sca4d.authentication.email.EmailCodeChallengeState;
import com.sca4d.common.exception.InternalServiceException;
import com.sca4d.common.redis.RedisScriptDefinition;
import com.sca4d.common.redis.RedisScriptExecutor;
import com.sca4d.config.properties.Sca4djAuthenticationProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/** 使用同一 Redis hash slot 原子维护每次认证的活跃邮件验证码 Challenge。 */
@Component
public class RedisEmailCodeChallengeStore implements EmailCodeChallengeStore {

    private static final RedisScriptDefinition<Long> CREATE_SCRIPT = new RedisScriptDefinition<>(
            "redis/authentication/email-code/create.lua", Long.class);
    private static final RedisScriptDefinition<Long> ADVANCE_SCRIPT = new RedisScriptDefinition<>(
            "redis/authentication/email-code/advance.lua", Long.class);
    private static final RedisScriptDefinition<Long> CANCEL_SCRIPT = new RedisScriptDefinition<>(
            "redis/authentication/email-code/cancel.lua", Long.class);
    private static final RedisScriptDefinition<String> GET_SCRIPT = new RedisScriptDefinition<>(
            "redis/get.lua", String.class);

    private final RedisScriptExecutor scripts;
    private final ObjectMapper objectMapper;
    private final Sca4djAuthenticationProperties properties;

    public RedisEmailCodeChallengeStore(
            @NotNull RedisScriptExecutor scripts,
            @NotNull ObjectMapper objectMapper,
            @NotNull Sca4djAuthenticationProperties properties) {
        this.scripts = scripts;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public boolean create(
            @NotNull EmailCodeChallenge challenge,
            @NotNull Duration ttl,
            @NotNull Duration resendInterval) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("邮件验证码 Challenge TTL 必须大于 0");
        }
        if (resendInterval.isZero() || resendInterval.isNegative()) {
            throw new IllegalArgumentException("邮件验证码重发间隔必须大于 0");
        }
        String prefix = properties.getEmailCode().getChallengeKeyPrefix() + ":{" + challenge.getAuthenticationId() + "}";
        String value;
        try {
            value = objectMapper.writeValueAsString(challenge);
        } catch (JsonProcessingException exception) {
            throw new InternalServiceException("邮件验证码 Challenge 序列化失败", exception);
        }
        Long created = scripts.execute(
                CREATE_SCRIPT,
                List.of(
                        prefix + ":challenge:" + challenge.getChallengeId(),
                        prefix + ":active",
                        prefix + ":cooldown"),
                value,
                Long.toString(Math.max(1, ttl.toMillis())),
                Long.toString(Math.max(1, resendInterval.toMillis())));
        return Long.valueOf(1L).equals(created);
    }

    @Override
    public @Nullable EmailCodeChallenge find(
            @NotNull String authenticationId, @NotNull String challengeId) {
        String prefix = properties.getEmailCode().getChallengeKeyPrefix() + ":{" + authenticationId + "}";
        String value = scripts.execute(
                GET_SCRIPT, List.of(prefix + ":challenge:" + challengeId));
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, EmailCodeChallenge.class);
        } catch (JsonProcessingException exception) {
            throw new InternalServiceException("邮件验证码 Challenge 反序列化失败", exception);
        }
    }

    @Override
    public boolean advance(
            @NotNull EmailCodeChallenge challenge,
            long expectedVersion,
            @NotNull EmailCodeChallengeState expectedState,
            boolean terminal) {
        String prefix = properties.getEmailCode().getChallengeKeyPrefix() + ":{" + challenge.getAuthenticationId() + "}";
        String value;
        try {
            value = objectMapper.writeValueAsString(challenge);
        } catch (JsonProcessingException exception) {
            throw new InternalServiceException("邮件验证码 Challenge 序列化失败", exception);
        }
        Long advanced = scripts.execute(
                ADVANCE_SCRIPT,
                List.of(
                        prefix + ":challenge:" + challenge.getChallengeId(),
                        prefix + ":active"),
                Long.toString(expectedVersion),
                expectedState.value(),
                value,
                terminal ? "1" : "0");
        return Long.valueOf(1L).equals(advanced);
    }

    @Override
    public void cancel(@NotNull String authenticationId, @NotNull String challengeId) {
        String prefix = properties.getEmailCode().getChallengeKeyPrefix() + ":{" + authenticationId + "}";
        scripts.execute(
                CANCEL_SCRIPT,
                List.of(
                        prefix + ":challenge:" + challengeId,
                        prefix + ":active",
                        prefix + ":cooldown"));
    }
}
