package com.sca4d.core.api.codec;

import org.jetbrains.annotations.NotNull;

/** 从自描述的外部表示中读取版本，供版本 Codec 选择读取策略。 */
public interface CodecVersionSelector<W> {

    /**
     * 读取外部表示声明的版本。
     *
     * @param wire 外部表示
     * @return 正整数版本
     */
    int version(@NotNull W wire);
}
