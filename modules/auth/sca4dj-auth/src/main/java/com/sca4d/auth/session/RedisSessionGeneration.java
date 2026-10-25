package com.sca4d.auth.session;

import com.sca4d.auth.exception.AuthException;
import com.sca4d.core.api.authentication.SessionInvalidationListener;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.spring.redis.KeyRedisSerializer;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** 在 Redis 中保存每个 Account 的 Session 失效代次。 */
public class RedisSessionGeneration {

    private static final Logger LOG = LoggerFactory.getLogger(RedisSessionGeneration.class);

    private final @NotNull RedisTemplate<Key, String> redis;

    private final @NotNull Supplier<@NotNull List<@NotNull SessionInvalidationListener>> listeners;

    private static final @NotNull Key KEY = Key.of(TreeCode.ofColon("AUTH:SESSION_GENERATION"));

    /**
     * 创建 Redis Session 代次存储。
     *
     * @param redis Redis 字符串操作
     * @param listeners 代次增加后读取当前已安装监听器的来源
     */
    public RedisSessionGeneration(
            @NotNull RedisTemplate<Key, String> redis,
            @NotNull Supplier<@NotNull List<@NotNull SessionInvalidationListener>> listeners) {
        this.redis = Objects.requireNonNull(redis, "Redis 操作不能为空");
        this.listeners = Objects.requireNonNull(listeners, "Session 失效监听器来源不能为空");
    }

    /**
     * 读取 Account 当前 Session 失效代次。
     *
     * @param accountId Account 标识
     * @return 尚未失效过时为零，否则返回 Redis 中的非负代次
     * @throws IllegalStateException Redis 中的代次不是非负整数时抛出
     */
    @CheckReturnValue
    public long current(long accountId) {
        String value = redis.opsForValue().get(key(accountId));
        if (value == null) {
            return 0;
        }
        try {
            long generation = Long.parseLong(value);
            if (generation < 0) {
                throw new IllegalStateException("Session 失效代次不能为负数");
            }
            return generation;
        } catch (NumberFormatException exception) {
            throw new AuthException("Session 失效代次不是整数", exception);
        }
    }

    /**
     * 原子增加 Account 的 Session 失效代次，再通知已安装的监听器。
     *
     * @param accountId 需要在下次请求刷新 Session 的 Account
     * @throws IllegalArgumentException Account 标识不是正数时抛出
     * @throws IllegalStateException Redis 没有返回新的失效代次时抛出
     */
    public void invalidate(long accountId) {
        if (accountId <= 0) {
            throw new IllegalArgumentException("Account 标识必须为正数");
        }
        Long generation = redis.opsForValue().increment(key(accountId));
        if (generation == null) {
            throw new IllegalStateException("Redis 没有返回 Session 失效代次");
        }
        List<SessionInvalidationListener> currentListeners = List.copyOf(
                Objects.requireNonNull(listeners.get(), "Session 失效监听器不能为空"));
        for (SessionInvalidationListener listener : currentListeners) {
            try {
                listener.invalidated(accountId);
            } catch (RuntimeException exception) {
                LOG.warn(
                        "Session 失效通知失败，已保留新的失效代次 | accountId={} generation={} listener={}",
                        accountId,
                        generation,
                        listener.getClass().getName(),
                        exception);
            }
        }
        LOG.info(
                "Account Session 已标记失效 | accountId={} generation={}",
                accountId,
                generation);
    }

    /**
     * 在一次 Redis pipeline 中分别原子增加多个 Account 的 Session 失效代次，再逐个通知监听器。
     *
     * @param accountIds 需要在下次请求刷新 Session 的 Account 标识集合；重复标识只增加一次
     * @throws NullPointerException 集合或元素为空时抛出
     * @throws IllegalArgumentException 任一 Account 标识不是正数时抛出
     * @throws IllegalStateException Redis 没有完整返回非负失效代次时抛出；此时不通知监听器
     */
    public void invalidate(@NotNull Collection<@NotNull Long> accountIds) {
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long accountId : Objects.requireNonNull(accountIds, "Account 标识集合不能为空")) {
            Long checkedId = Objects.requireNonNull(accountId, "Account 标识不能为空");
            if (checkedId <= 0) {
                throw new IllegalArgumentException("Account 标识必须为正数");
            }
            unique.add(checkedId);
        }
        if (unique.isEmpty()) {
            return;
        }

        List<Object> results = redis.executePipelined((RedisCallback<Object>) connection -> {
            for (Long accountId : unique) {
                connection.stringCommands().incr(KeyRedisSerializer.keyBytes(redis, key(accountId)));
            }
            return null;
        });
        if (results.size() != unique.size()) {
            throw new IllegalStateException("Redis 没有完整返回 Session 失效代次");
        }
        for (Object result : results) {
            if (!(result instanceof Long generation) || generation < 0) {
                throw new IllegalStateException("Redis 返回了非法 Session 失效代次");
            }
        }

        List<SessionInvalidationListener> currentListeners = List.copyOf(
                Objects.requireNonNull(listeners.get(), "Session 失效监听器不能为空"));
        for (Long accountId : unique) {
            for (SessionInvalidationListener listener : currentListeners) {
                try {
                    listener.invalidated(accountId);
                } catch (RuntimeException exception) {
                    LOG.warn(
                            "Session 失效通知失败，已保留新的失效代次 | accountId={} listener={}",
                            accountId,
                            listener.getClass().getName(),
                            exception);
                }
            }
        }
        LOG.info("Account Session 已批量标记失效 | accountCount={}", unique.size());
    }

    /**
     * 返回指定 Account 的 Redis 键。
     *
     * @param accountId Account 标识
     * @return Session 失效代次键
     */
    private @NotNull Key key(long accountId) {
        return KEY.part(accountId);
    }
}
