package com.sca4d.core.api.codec;

import org.jetbrains.annotations.NotNull;

/** 使用源版本解码和目标版本编码完成一个相邻版本迁移步骤。 */
public final class CodecMigrationStep<V, W> implements Migratable<W> {

    private final @NotNull VersionedCodec<V, W> source;

    private final @NotNull VersionedCodec<V, W> target;

    /**
     * 创建相邻版本迁移步骤。
     *
     * @param source 源版本 Codec
     * @param target 目标版本 Codec，版本必须比 source 大一
     */
    public CodecMigrationStep(
            @NotNull VersionedCodec<V, W> source,
            @NotNull VersionedCodec<V, W> target) {
        this.source = source;
        this.target = target;
        if (source.version() <= 0 || target.version() != source.version() + 1) {
            throw new IllegalArgumentException("迁移步骤必须只跨越一个正数版本");
        }
        if (!source.code().equals(target.code()) || !source.valueType().equals(target.valueType())) {
            throw new IllegalArgumentException("迁移步骤的稳定类型或 Java 类型不一致");
        }
    }

    @Override
    public int sourceVersion() {
        return source.version();
    }

    @Override
    public int targetVersion() {
        return target.version();
    }

    @Override
    public @NotNull W migrate(@NotNull W sourceWire) {
        return target.encode(source.decode(sourceWire));
    }
}
