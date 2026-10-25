package com.sca4d.core.spring.redis;

import com.sca4d.core.api.isolation.AppName;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.NameFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 序列化和反序列化 Redis key 字节时应用 App 隔离，并按模块声明把同组 key 放入同一个 Cluster 槽。 */
public class KeyRedisSerializer implements RedisSerializer<Key> {

    private final @NotNull List<RedisHashTag> hashTags;

    /** 保存同槽声明，并按根编码长度从长到短排序，较长的根优先匹配；构造时要求 AppName 已经初始化。 */
    public KeyRedisSerializer(@NotNull List<RedisHashTag> hashTags) {
        AppName.current();
        this.hashTags = Objects.requireNonNull(hashTags)
                .stream()
                .sorted(Comparator.comparingInt((RedisHashTag tag) -> tag.root().size()).reversed())
                .toList();
    }

    /** 使用模板实际安装的序列化器为 pipeline 和 Lua 输出完整 key 字节。 */
    public static byte @NotNull [] keyBytes(@NotNull RedisTemplate<Key, String> redis, @NotNull Key key) {
        if (!(redis.getKeySerializer() instanceof KeyRedisSerializer serializer)) {
            throw new IllegalStateException("脚手架 Redis 模板必须使用 KeyRedisSerializer");
        }
        return Objects.requireNonNull(serializer.serialize(key));
    }

    /** 输出 Lua 动态 key 使用的物理名称，包含 App 前缀和同槽标记。 */
    public static @NotNull String keyText(@NotNull RedisTemplate<Key, String> redis, @NotNull Key key) {
        return new String(keyBytes(redis, key), StandardCharsets.UTF_8);
    }

    @Override
    public byte @Nullable [] serialize(@Nullable Key key) {
        if (key == null) {
            return null;
        }
        String encoded = key.format(NameFormat.COLON);
        for (RedisHashTag tag : hashTags) {
            if (key.isUnder(tag.root())) {
                encoded = AppName.current().format(NameFormat.COLON) + ":{"
                        + Key.of(tag.root()).format(NameFormat.COLON) + "}:" + NameFormat.COLON.join(key.segments());
                break;
            }
        }
        return encoded.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public @Nullable Key deserialize(byte @Nullable [] bytes) {
        if (bytes == null) {
            return null;
        }
        String physical = new String(bytes, StandardCharsets.UTF_8);
        String tagged = AppName.current().format(NameFormat.COLON) + ":{";
        Key key;
        if (physical.startsWith(tagged)) {
            int end = physical.indexOf("}:", tagged.length());
            if (end < 0) {
                throw new IllegalArgumentException("Redis key 同槽标记无效");
            }
            key = Key.fromSegments(List.of(physical.substring(end + 2).split(":", -1)));
        } else {
            key = Key.parse(physical, NameFormat.COLON);
        }
        if (!physical.equals(new String(Objects.requireNonNull(serialize(key)), StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Redis key 的 App 前缀、同槽标记或编码不匹配");
        }
        return key;
    }
}
