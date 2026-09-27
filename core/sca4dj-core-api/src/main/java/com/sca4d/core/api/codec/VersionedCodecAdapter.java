package com.sca4d.core.api.codec;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

/** 把多个版本 Codec 适配为一个运行时 Codec，编码使用当前版本，解码按 wire 版本选择策略。 */
public class VersionedCodecAdapter<V, W> implements VersionedCodec<V, W> {

    private final @NotNull VersionedCodecCatalog<V, W> catalog;

    private final @NotNull CodecVersionSelector<W> selector;

    /**
     * 创建自描述 wire 的版本适配器。
     *
     * @param catalog 同一稳定类型的版本 Codec 目录
     * @param selector 从 wire 读取版本的选择器
     */
    public VersionedCodecAdapter(
            @NotNull VersionedCodecCatalog<V, W> catalog,
            @NotNull CodecVersionSelector<W> selector) {
        this.catalog = catalog;
        this.selector = selector;
    }

    @Override
    public @NotNull TreeCode code() {
        return catalog.code();
    }

    @Override
    public int version() {
        return catalog.version();
    }

    @Override
    public @NotNull Class<V> valueType() {
        return catalog.valueType();
    }

    @Override
    public @NotNull W encode(@NotNull V value) {
        return catalog.current().encode(value);
    }

    @Override
    public @NotNull V decode(@NotNull W wire) {
        return catalog.select(selector.version(wire)).decode(wire);
    }

    /**
     * 使用外部保存的版本解码，例如数据库单独保存 schema_version 的场景。
     *
     * @param version 外部保存的版本
     * @param wire 外部表示
     * @return 解码后的领域值
     */
    public @NotNull V decodeAt(int version, @NotNull W wire) {
        return catalog.select(version).decode(wire);
    }
}
