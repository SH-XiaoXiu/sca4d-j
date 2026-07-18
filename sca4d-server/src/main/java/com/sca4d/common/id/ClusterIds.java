package com.sca4d.common.id;

import org.jetbrains.annotations.NotNull;

/**
 * 非 Spring 场景下的 ID 生成入口。
 */
public final class ClusterIds {

    private static volatile IdGenerator generator = new ClusterIdGenerator(1, 1);

    private ClusterIds() {
    }

    /**
     * 生成 Long ID。
     *
     * @return Long ID
     */
    public static long nextId() {
        return generator.nextId();
    }

    /**
     * 生成字符串 ID。
     *
     * @return 字符串 ID
     */
    public static @NotNull String nextIdStr() {
        return generator.nextIdStr();
    }

    /**
     * 设置全局 ID 生成器。
     *
     * @param generator ID 生成器
     */
    public static void setGenerator(@NotNull IdGenerator generator) {
        ClusterIds.generator = generator;
    }
}
