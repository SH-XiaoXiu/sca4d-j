package com.sca4d.core.spring.redis;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 声明一个编码根，使该根下的 Redis key 在 Redis Cluster 中落在同一个槽位。
 *
 * <p>{@code KeyRedisSerializer} 序列化 key 时，匹配该根的 key 会把根编码放入花括号 hash tag。
 */
public class RedisHashTag {

    private final @NotNull TreeCode root;

    /**
     * 创建同槽声明。
     *
     * @param root 同槽编码根，位于该根下的 key 共享同一个 hash tag
     * @throws NullPointerException 编码根为空时抛出
     */
    public RedisHashTag(@NotNull TreeCode root) {
        this.root = Objects.requireNonNull(root, "同槽根编码不能为空");
    }

    /**
     * 返回同槽编码根。
     *
     * @return 构造时传入的编码根
     */
    public @NotNull TreeCode root() {
        return root;
    }
}
