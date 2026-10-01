package com.sca4d.core.api.codec;

import org.jetbrains.annotations.NotNull;

/** 表示一次只跨越相邻两个序列化版本的迁移步骤。 */
public interface Migratable<W> {

    /** @return 输入外部表示的版本 */
    int sourceVersion();

    /** @return 输出外部表示的版本，必须比源版本大一 */
    int targetVersion();

    /**
     * 把一个版本的外部表示转换为下一个版本的外部表示。
     *
     * @param sourceWire 源版本外部表示
     * @return 目标版本外部表示
     */
    @NotNull
    W migrate(@NotNull W sourceWire);
}
