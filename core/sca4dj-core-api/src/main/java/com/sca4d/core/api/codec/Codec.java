package com.sca4d.core.api.codec;

import org.jetbrains.annotations.NotNull;

/** 定义领域值与外部表示之间的双向转换。 */
public interface Codec<V, W> {

    /** @return value 对应的外部表示 */
    @NotNull
    W encode(@NotNull V value);

    /** @return wire 恢复出的领域值 */
    @NotNull
    V decode(@NotNull W wire);
}
