package com.sca4d.auth.federation;

import com.sca4d.auth.federation.exception.AuthFederationException;
import com.sca4d.core.api.data.DataOps;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.spring.redis.Sca4djRedis;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** 使用 App 隔离名称隔离的 Redis 一次性值保存外部授权状态和标准化资料。 */
public class RedisFederatedAuthorizationStore implements FederatedAuthorizationStore {

    private static final Logger LOG = LoggerFactory.getLogger(RedisFederatedAuthorizationStore.class);

    private final @NotNull RedisTemplate<Key, String> redis;

    private final @NotNull Key authorizationPrefix;

    private final @NotNull Key completionPrefix;

    /** 创建外部授权状态存储。 */
    public RedisFederatedAuthorizationStore(
            @Sca4djRedis @NotNull RedisTemplate<Key, String> redis) {
        this.redis = Objects.requireNonNull(redis, "Redis 操作不能为空");

        this.authorizationPrefix = Key.of(TreeCode.ofColon("AUTHENTICATION:FEDERATED:AUTHORIZATION"));
        this.completionPrefix = Key.of(TreeCode.ofColon("AUTHENTICATION:FEDERATED:COMPLETION"));
    }

    @Override
    public void save(@NotNull FederatedAuthorization authorization, @NotNull Duration ttl) {
        FederatedAuthorization checked = Objects.requireNonNull(authorization, "授权状态不能为空");
        redis.opsForValue().set(key(authorizationPrefix, checked.id()), encodeAuthorization(checked), checkedTtl(ttl));
        LOG.trace(
                "联邦授权状态已保存 | providerCode={} purpose={} ttlSeconds={}",
                checked.providerCode(),
                checked.purpose(),
                ttl.toSeconds());
    }

    @Override
    public @Nullable FederatedAuthorization consume(@NotNull String id, @NotNull String browserBinding) {
        String json = redis.opsForValue().getAndDelete(key(authorizationPrefix, id));
        if (json == null) {
            LOG.trace("联邦授权状态消费完成 | available=false");
            return null;
        }
        try {
            DataOps.JsonPipe node = DataOps.j(json);
            if (!Objects.equals(browserBinding, node.requiredText("browserBinding"))
                    || Instant.ofEpochMilli(node.requiredLong("expiresAt")).isBefore(Instant.now())) {
                LOG.trace("联邦授权状态消费完成 | available=true accepted=false");
                return null;
            }
            FederatedAuthorization authorization = new FederatedAuthorization(
                    node.requiredText("id"),
                    node.requiredText("providerCode"),
                    purpose(node.requiredText("purpose")),
                    node.requiredText("browserBinding"),
                    node.requiredText("nonce"),
                    node.requiredText("codeVerifier"),
                    node.requiredText("redirectUri"),
                    Instant.ofEpochMilli(node.requiredLong("expiresAt")));
            LOG.trace(
                    "联邦授权状态消费完成 | available=true accepted=true providerCode={} purpose={}",
                    authorization.providerCode(),
                    authorization.purpose());
            return authorization;
        } catch (Exception exception) {
            throw new AuthFederationException("外部授权 Redis 数据损坏", exception);
        }
    }

    @Override
    public void saveCompletion(
            @NotNull String id,
            @NotNull String providerCode,
            @NotNull FederatedAuthorizationPurpose purpose,
            @NotNull FederatedProfile profile,
            @NotNull String browserBinding,
            @NotNull Duration ttl) {
        FederatedProfile checkedProfile = Objects.requireNonNull(profile, "外部资料不能为空");
        String value = DataOps.obj()
                .put("providerCode", providerCode)
                .put("purpose", Objects.requireNonNull(purpose, "授权用途不能为空").name())
                .put("browserBinding", browserBinding)
                .put("issuer", checkedProfile.issuer())
                .put("subject", checkedProfile.subject())
                .put("displayName", checkedProfile.displayName())
                .put("email", checkedProfile.email())
                .put("emailVerified", checkedProfile.emailVerified())
                .put("avatarUrl", checkedProfile.avatarUrl())
                .req("联邦授权完成结果")
                .get();
        redis.opsForValue().set(key(completionPrefix, id), value, checkedTtl(ttl));
        LOG.trace(
                "联邦授权完成结果已保存 | providerCode={} purpose={} ttlSeconds={}",
                providerCode,
                purpose,
                ttl.toSeconds());
    }

    @Override
    public @Nullable FederatedCompletedAuthorization consumeCompletion(
            @NotNull String id,
            @NotNull String browserBinding,
            @NotNull FederatedAuthorizationPurpose expectedPurpose) {
        String json = redis.opsForValue().getAndDelete(key(completionPrefix, id));
        if (json == null) {
            LOG.trace("联邦授权完成结果消费完成 | available=false expectedPurpose={}", expectedPurpose);
            return null;
        }
        try {
            DataOps.JsonPipe node = DataOps.j(json);
            if (!Objects.equals(browserBinding, node.requiredText("browserBinding"))) {
                LOG.trace("联邦授权完成结果消费完成 | available=true accepted=false expectedPurpose={}", expectedPurpose);
                return null;
            }
            FederatedAuthorizationPurpose purpose = purpose(node.requiredText("purpose"));
            if (purpose != expectedPurpose) {
                LOG.trace("联邦授权完成结果消费完成 | available=true accepted=false expectedPurpose={}", expectedPurpose);
                return null;
            }
            FederatedCompletedAuthorization completed = new FederatedCompletedAuthorization(
                    node.requiredText("providerCode"),
                    purpose,
                    new FederatedProfile(
                            nullable(node, "issuer"),
                            node.requiredText("subject"),
                            nullable(node, "displayName"),
                            nullable(node, "email"),
                            node.requiredBoolean("emailVerified"),
                            nullable(node, "avatarUrl")));
            LOG.trace(
                    "联邦授权完成结果消费完成 | available=true accepted=true providerCode={} purpose={}",
                    completed.providerCode(),
                    completed.purpose());
            return completed;
        } catch (Exception exception) {
            throw new AuthFederationException("外部资料 Redis 数据损坏", exception);
        }
    }

    /** 校验一次性标识格式，并拼接到对应内部键前缀。 */
    private static Key key(Key prefix, String id) {
        String checked = Objects.requireNonNull(id, "授权标识不能为空");
        if (!checked.matches("[A-Za-z0-9_-]{20,128}"))
            throw new IllegalArgumentException("授权标识格式无效");
        return prefix.part(checked);
    }

    /** 限制授权状态 TTL，避免错误配置产生长期凭据。 */
    private Duration checkedTtl(Duration ttl) {
        Duration checked = Objects.requireNonNull(ttl, "授权 TTL 不能为空");
        if (checked.isZero() || checked.isNegative() || checked.compareTo(Duration.ofMinutes(15)) > 0) {
            throw new IllegalArgumentException("外部授权 TTL 必须大于 0 且不超过 15 分钟");
        }
        return checked;
    }

    /** 使用 DataOps 编码授权状态，确保写入 Redis 的值是对象 JSON。 */
    private String encodeAuthorization(FederatedAuthorization authorization) {
        return DataOps.obj()
                .put("id", authorization.id())
                .put("providerCode", authorization.providerCode())
                .put("purpose", authorization.purpose().name())
                .put("browserBinding", authorization.browserBinding())
                .put("nonce", authorization.nonce())
                .put("codeVerifier", authorization.codeVerifier())
                .put("redirectUri", authorization.redirectUri())
                .put("expiresAt", authorization.expiresAt().toEpochMilli())
                .req("联邦授权状态")
                .get();
    }

    /** 把 Redis 中的授权用途文本转换为受限枚举。 */
    private static FederatedAuthorizationPurpose purpose(String value) {
        try {
            return FederatedAuthorizationPurpose.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new AuthFederationException("联邦授权用途无效", exception);
        }
    }

    /** 读取允许缺失的 Redis JSON 文本字段。 */
    private static @Nullable String nullable(DataOps.JsonPipe node, String field) {
        return node.text(field).opt();
    }
}
