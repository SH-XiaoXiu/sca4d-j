package com.sca4d.auth.passkey.ceremony;

import static java.lang.Boolean.TRUE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.auth.passkey.PasskeyCeremonyId;
import com.sca4d.auth.passkey.PasskeyErrorCode;
import com.sca4d.auth.passkey.config.PasskeySettings;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 使用 Redis TTL 保存 WebAuthn ceremony，并在校验前通过 GETDEL 原子消费。
 */
public final class PasskeyCeremonyStore {

    private final RedisTemplate<Key, String> redis;

    private final ObjectMapper objectMapper;

    private final Clock clock;

    private final PasskeySettings settings;

    private static final TreeCode REGISTRATION = TreeCode.ofColon("PASSKEY:REGISTRATION");

    private static final TreeCode ASSERTION = TreeCode.ofColon("PASSKEY:ASSERTION");

    /**
     * 创建 Passkey ceremony 存储。
     *
     * @param redis            Redis 字符串操作
     * @param objectMapper     固定 ceremony 结构使用的 JSON 映射器
     * @param clock            过期时间来源
     * @param settings         每次保存 ceremony 时读取有效期的 Settings
     */
    public PasskeyCeremonyStore(
            @NotNull RedisTemplate<Key, String> redis,
            @NotNull ObjectMapper objectMapper,
            @NotNull Clock clock,
            @NotNull PasskeySettings settings) {
        this.redis = Objects.requireNonNull(redis, "Redis 操作不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "Passkey JSON 映射器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.settings = Objects.requireNonNull(settings, "Settings 不能为空");
    }

    /**
     * 保存绑定账号的 registration request。
     */
    public @NotNull StoredCeremony storeRegistration(long accountId, @NotNull String requestJson) {
        if (accountId <= 0) {
            throw new IllegalArgumentException("Passkey registration Account 标识必须为正数");
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("version", 1);
        payload.put("accountId", accountId);
        payload.put("request", Objects.requireNonNull(requestJson, "Passkey registration request 不能为空"));
        return store(REGISTRATION, payload);
    }

    /**
     * 保存不预先绑定账号的 discoverable assertion request。
     */
    public @NotNull StoredCeremony storeAssertion(@NotNull String requestJson) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("version", 1);
        payload.put("request", Objects.requireNonNull(requestJson, "Passkey assertion request 不能为空"));
        return store(ASSERTION, payload);
    }

    /**
     * 在验证响应前原子消费 registration request。
     */
    public @NotNull RegistrationCeremony consumeRegistration(@NotNull PasskeyCeremonyId id) {
        JsonNode payload = consume(REGISTRATION, id);
        long accountId = payload.path("accountId").asLong();
        if (accountId <= 0) {
            throw new BusinessException(PasskeyErrorCode.CEREMONY_UNAVAILABLE);
        }
        return new RegistrationCeremony(accountId, requiredRequest(payload));
    }

    /**
     * 在验证响应前原子消费 assertion request。
     */
    public @NotNull String consumeAssertion(@NotNull PasskeyCeremonyId id) {
        return requiredRequest(consume(ASSERTION, id));
    }

    /**
     * 使用随机标识和 TTL 保存 ceremony；连续冲突时拒绝创建。
     */
    private StoredCeremony store(TreeCode prefix, ObjectNode payload) {
        Duration ttl = settings.CEREMONY_TTL.get();
        Instant expiresAt = clock.instant().plus(ttl);
        for (int attempt = 0; attempt < 3; attempt++) {
            PasskeyCeremonyId id = new PasskeyCeremonyId(UUID.randomUUID().toString());
            try {
                Boolean created = redis.opsForValue()
                        .setIfAbsent(
                                Key.of(prefix).part(id.value()),
                                objectMapper.writeValueAsString(payload),
                                ttl);
                if (TRUE.equals(created)) {
                    return new StoredCeremony(id, expiresAt);
                }
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Passkey ceremony 编码失败", exception);
            }
        }
        throw new IllegalStateException("Passkey ceremony 标识连续冲突");
    }

    /**
     * 原子读取并删除 ceremony，拒绝不存在、过期或版本不兼容的数据。
     */
    private JsonNode consume(TreeCode prefix, PasskeyCeremonyId id) {
        String encoded = redis.opsForValue()
                .getAndDelete(
                        Key.of(prefix).part(Objects.requireNonNull(id, "Passkey ceremony 标识不能为空").value()));
        if (encoded == null) {
            throw new BusinessException(PasskeyErrorCode.CEREMONY_UNAVAILABLE);
        }
        try {
            JsonNode payload = objectMapper.readTree(encoded);
            if (payload.path("version").asInt() != 1) {
                throw new BusinessException(PasskeyErrorCode.CEREMONY_UNAVAILABLE);
            }
            return payload;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(PasskeyErrorCode.CEREMONY_UNAVAILABLE, exception);
        }
    }

    /**
     * 从已校验版本的 ceremony 中读取非空 Yubico request JSON。
     */
    private static String requiredRequest(JsonNode payload) {
        String request = payload.path("request").asText();
        if (request.isBlank()) {
            throw new BusinessException(PasskeyErrorCode.CEREMONY_UNAVAILABLE);
        }
        return request;
    }

    /**
     * 保存 ceremony 后返回不透明标识和过期时间。
     */
    public static final class StoredCeremony {
        private final PasskeyCeremonyId id;

        private final Instant expiresAt;

        StoredCeremony(PasskeyCeremonyId id, Instant expiresAt) {
            this.id = id;
            this.expiresAt = expiresAt;
        }

        /**
         * 返回浏览器完成 ceremony 时必须提交的不透明标识。
         */
        public PasskeyCeremonyId id() {
            return id;
        }

        /**
         * 返回 ceremony 失效时间。
         */
        public Instant expiresAt() {
            return expiresAt;
        }
    }

    /**
     * 原子消费后恢复的 registration 账号和 Yubico request。
     */
    public static final class RegistrationCeremony {
        private final long accountId;

        private final String requestJson;

        RegistrationCeremony(long accountId, String requestJson) {
            this.accountId = accountId;
            this.requestJson = requestJson;
        }

        /**
         * 返回开始 registration 时绑定的账号标识。
         */
        public long accountId() {
            return accountId;
        }

        /**
         * 返回 Yubico 完成 registration 所需的原始 request JSON。
         */
        public String requestJson() {
            return requestJson;
        }
    }
}
