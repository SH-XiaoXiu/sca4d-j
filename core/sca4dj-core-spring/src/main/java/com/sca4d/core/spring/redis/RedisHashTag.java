package com.sca4d.core.spring.redis;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 声明需要在 Redis Cluster 中共同参与 Lua 操作的一组逻辑 key。 */
public class RedisHashTag {

    private final @NotNull TreeCode root;

    public RedisHashTag(@NotNull TreeCode root) {
        this.root = Objects.requireNonNull(root, "同槽根编码不能为空");
    }

    /**
     * 需要共享 Cluster hash tag 的编码根。
     *
     * @return 需要共享 Cluster hash tag 的编码根
     */
    public @NotNull TreeCode root() {
        return root;
    }
}
