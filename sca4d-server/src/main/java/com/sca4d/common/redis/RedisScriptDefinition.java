package com.sca4d.common.redis;

import com.sca4d.common.exception.InternalServiceException;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 启动时从 classpath 读取并缓存的类型化 Redis Lua 脚本。 */
public final class RedisScriptDefinition<T> {

    private final DefaultRedisScript<T> script;

    public RedisScriptDefinition(@NotNull String resourcePath, @NotNull Class<T> resultType) {
        try {
            String source = new ClassPathResource(resourcePath).getContentAsString(StandardCharsets.UTF_8);
            this.script = new DefaultRedisScript<>(source, resultType);
        } catch (IOException exception) {
            throw new InternalServiceException("Redis Lua 资源读取失败: " + resourcePath, exception);
        }
    }

    @NotNull
    DefaultRedisScript<T> script() {
        return script;
    }
}
