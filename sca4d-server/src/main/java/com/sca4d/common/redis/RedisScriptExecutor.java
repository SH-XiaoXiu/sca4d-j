package com.sca4d.common.redis;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/** Spring Redis 客户端与业务 Lua 脚本之间的唯一执行边界。 */
@Component
public class RedisScriptExecutor {

    private final StringRedisTemplate redisTemplate;

    public RedisScriptExecutor(@NotNull StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** @return Lua 脚本的类型化结果 */
    public <T> @Nullable T execute(
            @NotNull RedisScriptDefinition<T> script,
            @NotNull List<String> keys,
            @NotNull String... arguments) {
        return redisTemplate.execute(script.script(), keys, (Object[]) arguments);
    }
}
