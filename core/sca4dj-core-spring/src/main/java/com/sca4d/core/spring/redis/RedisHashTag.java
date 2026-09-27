package com.sca4d.core.spring.redis;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 声明需要在 Redis Cluster 中共同参与 Lua 操作的一组逻辑 key。 */
public final class RedisHashTag {

    private final @NotNull TreeCode root;

    /** 创建同槽 key 的根编码。 */
    public RedisHashTag(@NotNull TreeCode root) {
        this.root = Objects.requireNonNull(root, "同槽根编码不能为空");
    }

    /** 返回需要共享 Cluster hash tag 的编码根。 */
    public @NotNull TreeCode root() {
        return root;
    }
}
