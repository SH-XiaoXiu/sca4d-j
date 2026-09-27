package com.sca4d.auth.session;

import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** 保存 SessionId 到 Sa-Token 不透明 Token 的短期索引，使 JWT 恢复的 Session 可以注销权威状态。 */
public final class RedisSessionTokenIndex {

    private final RedisTemplate<Key, String> redis;

    private final Key keyPrefix;

    /**
     * 创建 Redis Session Token 索引。
     *
     * @param redis Redis 字符串操作
     */
    public RedisSessionTokenIndex(
            @NotNull RedisTemplate<Key, String> redis) {
        this.redis = Objects.requireNonNull(redis, "Redis 操作不能为空");
        this.keyPrefix = Key.of(TreeCode.ofColon("AUTH:SESSION_TOKEN"));
    }

    /**
     * 保存与 backing Session 相同有效期的 Token 索引。
     *
     * @param sessionId Session 标识
     * @param tokenValue 不透明 Sa-Token Token
     * @param ttl 必须大于零的剩余有效期
     */
    public void save(@NotNull SessionId sessionId, @NotNull String tokenValue, @NotNull Duration ttl) {
        String checkedToken = Objects.requireNonNull(tokenValue, "Session Token 不能为空");
        Duration checkedTtl = Objects.requireNonNull(ttl, "Session Token 索引有效期不能为空");
        if (checkedToken.isBlank()) {
            throw new IllegalArgumentException("Session Token 不能为空");
        }
        if (checkedTtl.isZero() || checkedTtl.isNegative()) {
            throw new IllegalArgumentException("Session Token 索引有效期必须为正数");
        }
        redis.opsForValue().set(key(sessionId), checkedToken, checkedTtl);
    }

    /**
     * 查找 Session 对应的不透明 Token。
     *
     * @param sessionId Session 标识
     * @return 索引存在时返回 Token，否则返回空结果
     */
    @CheckReturnValue
    public @NotNull Optional<String> find(@NotNull SessionId sessionId) {
        return Optional.ofNullable(redis.opsForValue().get(key(sessionId)));
    }

    /**
     * 删除 Session Token 索引。
     *
     * @param sessionId Session 标识
     */
    public void delete(@NotNull SessionId sessionId) {
        redis.delete(key(sessionId));
    }

    private Key key(SessionId sessionId) {
        return keyPrefix.part(Objects.requireNonNull(sessionId, "Session 标识不能为空").value());
    }
}
