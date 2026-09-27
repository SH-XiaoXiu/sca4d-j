package com.sca4d.auth.hmac;

import static java.lang.Boolean.TRUE;

import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Objects;

/** 使用 Redis 保存 MAC nonce 一次性消费记录和账号失败窗口。 */
public final class HmacAuthenticationAttemptStore {
    private final @NotNull RedisTemplate<Key, String> redis;

    private final @NotNull Key keyPrefix;

    /** 创建按 App 隔离名称隔离的 MAC 认证尝试存储。 */
    public HmacAuthenticationAttemptStore(
            @NotNull RedisTemplate<Key, String> redis) {
        this.redis = Objects.requireNonNull(redis, "HMAC Redis 不能为空");
        this.keyPrefix = Key.of(TreeCode.ofColon("AUTH:HMAC"));
    }

    /** @return Account 和 nonce 在 TTL 内首次消费时为 {@code true} */
    public boolean consumeNonce(long accountId, @NotNull String nonce, @NotNull Duration ttl) {
        Boolean consumed = redis.opsForValue()
                .setIfAbsent(
                        nonceKey(accountId, nonce),
                        "1",
                        positive(ttl, "HMAC nonce TTL"));
        if (consumed == null) {
            throw new IllegalStateException("Redis 没有返回 HMAC nonce 写入结果");
        }
        return consumed;
    }

    /** @return 当前失败窗口内的认证失败次数 */
    public int failureCount(long accountId) {
        String value = redis.opsForValue().get(failureKey(accountId));
        if (value == null) {
            return 0;
        }
        try {
            int count = Integer.parseInt(value);
            if (count < 0) {
                throw new IllegalStateException("HMAC 失败计数不能为负数");
            }
            return count;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("HMAC 失败计数不是整数", exception);
        }
    }

    /** @return 增加后的失败次数；首次失败同时建立固定窗口 TTL */
    public long recordFailure(long accountId, @NotNull Duration window) {
        Key key = failureKey(accountId);
        Long count = redis.opsForValue().increment(key);
        if (count == null || count < 1) {
            throw new IllegalStateException("Redis 没有返回有效的 HMAC 失败计数");
        }
        if (count == 1 && !TRUE.equals(redis.expire(key, positive(window, "HMAC 失败窗口")))) {
            redis.delete(key);
            throw new IllegalStateException("Redis 未能设置 HMAC 失败计数有效期");
        }
        return count;
    }

    /** 清除 Account 的失败窗口；nonce 继续由各自 TTL 到期。 */
    public void clearFailures(long accountId) {
        redis.delete(failureKey(accountId));
    }

    /** @return Account 内一次性 nonce key */
    private @NotNull Key nonceKey(long accountId, @NotNull String nonce) {
        return keyPrefix.append(TreeCode.ofSegment("NONCE"))
                .part(accountId)
                .part(Objects.requireNonNull(nonce, "HMAC nonce 不能为空"));
    }

    /** @return Account 失败窗口 key */
    private @NotNull Key failureKey(long accountId) {
        return keyPrefix.append(TreeCode.ofSegment("FAILURE")).part(accountId);
    }

    /** @return 严格为正的 Redis TTL */
    private static @NotNull Duration positive(@NotNull Duration value, @NotNull String name) {
        Duration checked = Objects.requireNonNull(value, name + "不能为空");
        if (checked.isZero() || checked.isNegative()) {
            throw new IllegalArgumentException(name + "必须为正数");
        }
        return checked;
    }
}
