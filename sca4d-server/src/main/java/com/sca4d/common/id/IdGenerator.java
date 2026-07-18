package com.sca4d.common.id;

import org.jetbrains.annotations.NotNull;

/**
 * 共用 ID 生成器。
 */
public interface IdGenerator {

    /**
     * 生成 Long ID。
     *
     * @return Long ID
     */
    long nextId();

    /**
     * 生成字符串 ID。
     *
     * @return 字符串 ID
     */
    default @NotNull String nextIdStr() {
        return Long.toString(nextId());
    }
}
