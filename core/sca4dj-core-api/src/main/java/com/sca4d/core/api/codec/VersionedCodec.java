package com.sca4d.core.api.codec;

import com.sca4d.core.api.code.TreeCode;
import org.jetbrains.annotations.NotNull;

/** 为需要持久化或跨部署恢复的值声明稳定编码、版本和 Java 类型。 */
public interface VersionedCodec<V, W> extends Codec<V, W> {

    /** @return 跨部署保持稳定的层级编码 */
    @NotNull
    TreeCode code();

    /** @return 当前外部表示的正数版本 */
    int version();

    /** @return 当前 Codec 接受的具体 Java 类型 */
    @NotNull
    Class<V> valueType();
}
