package com.sca4d.core.api.codec;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** 保存同一稳定类型的多个版本 Codec，并选择当前写入版本或指定读取版本。 */
public final class VersionedCodecCatalog<V, W> {

    private final @NotNull VersionedCodec<V, W> current;

    private final @NotNull Map<Integer, @NotNull VersionedCodec<V, W>> codecs;

    /**
     * 创建版本 Codec 目录。
     *
     * @param current 当前写入使用的 Codec
     * @param codecs 同一稳定类型的全部读取 Codec，必须包含 current
     */
    public VersionedCodecCatalog(
            @NotNull VersionedCodec<V, W> current,
            @NotNull Collection<? extends @NotNull VersionedCodec<V, W>> codecs) {
        this.current = checked(current, "当前 Codec");
        Map<Integer, VersionedCodec<V, W>> indexed = new LinkedHashMap<>();
        boolean currentIncluded = false;
        for (VersionedCodec<V, W> candidate : nonNull(codecs, "Codec 列表不能为空")) {
            VersionedCodec<V, W> codec = checked(candidate, "版本 Codec");
            if (!codec.code().equals(this.current.code())
                    || !codec.valueType().equals(this.current.valueType())) {
                throw new IllegalArgumentException("版本 Codec 的稳定类型或 Java 类型不一致");
            }
            if (indexed.putIfAbsent(codec.version(), codec) != null) {
                throw new IllegalArgumentException("重复的版本 Codec: " + codec.version());
            }
            currentIncluded |= codec == this.current;
        }
        if (!currentIncluded) {
            throw new IllegalArgumentException("Codec 列表没有包含当前 Codec");
        }
        this.codecs = Map.copyOf(indexed);
    }

    /** @return 当前写入版本 Codec */
    public @NotNull VersionedCodec<V, W> current() {
        return current;
    }

    /** @return 稳定类型编码 */
    public @NotNull TreeCode code() {
        return current.code();
    }

    /** @return 当前 Codec 接收的 Java 类型 */
    public @NotNull Class<V> valueType() {
        return current.valueType();
    }

    /** @return 当前写入版本 */
    public int version() {
        return current.version();
    }

    /** @param version 要查找的版本 @return 找到时返回对应 Codec */
    public @NotNull Optional<@NotNull VersionedCodec<V, W>> find(int version) {
        return Optional.ofNullable(codecs.get(version));
    }

    /**
     * 按外部表示声明的版本选择读取 Codec。
     *
     * @param version 外部表示版本
     * @return 对应版本 Codec
     */
    public @NotNull VersionedCodec<V, W> select(int version) {
        VersionedCodec<V, W> codec = codecs.get(version);
        if (codec == null) {
            throw new IllegalArgumentException("序列化版本不受支持: " + version);
        }
        return codec;
    }

    private static <V, W> @NotNull VersionedCodec<V, W> checked(
            VersionedCodec<V, W> codec,
            String name) {
        VersionedCodec<V, W> checked = nonNull(codec, name + "不能为空");
        if (checked.version() <= 0) {
            throw new IllegalArgumentException(name + "版本必须为正数");
        }
        return checked;
    }

    private static <T> @NotNull T nonNull(T value, String message) {
        if (value == null) {
            throw new NullPointerException(message);
        }
        return value;
    }
}
