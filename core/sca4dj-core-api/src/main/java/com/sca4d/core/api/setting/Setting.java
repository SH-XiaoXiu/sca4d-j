package com.sca4d.core.api.setting;

import com.sca4d.core.api.codec.Codec;
import com.sca4d.core.api.codec.VersionedCodec;
import com.sca4d.core.api.codec.VersionedCodecCatalog;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.access.SettingWriter;
import com.sca4d.core.api.setting.value.SettingOwner;
import com.sca4d.core.api.setting.value.SettingValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/** 直接读取一项类型化设置，按构造声明选择所属、版本 Codec、继承及覆盖规则。 */
public class Setting<T> {
    private final TreeCode code;

    private final VersionedCodec<T, ?> codec;

    private final Function<T, T> copy;

    private final Pipeline<T, T> validation;

    private final T defaultValue;

    private final java.util.function.Supplier<SettingOwner> owner;

    private final boolean secret;

    private final List<BiFunction<TreeCode, SettingOwner, SettingValue<T>>> readers;

    private final List<Overlay<T>> overlays;

    /** 固定构造配置并校验默认值；不会在启动后补写 IO 依赖。 */
    protected Setting(@NotNull Builder<T, ?> builder) {
        code = builder.code;
        codec = builder.current;
        copy = builder.copy;
        validation = builder.validation;
        owner = builder.owner;
        secret = builder.secret;
        readers = List.copyOf(builder.readers);
        overlays = List.copyOf(builder.overlays);
        defaultValue = builder.defaultValue == null ? null : validate(copy.apply(builder.defaultValue));
        builder.frozen = true;
    }

    /** 用领域 Codec 创建构造器，值类型和内容版本均从 Codec 获取。 */
    public static <T, W> @NotNull Builder<T, W> builder(@NotNull TreeCode code, @NotNull VersionedCodec<T, W> codec) {
        return new Builder<>(code, codec);
    }

    /**
     * 设置身份，序列化格式不参与身份判断。
     *
     * @return 设置身份，序列化格式不参与身份判断
     */
    public @NotNull TreeCode code() {
        return code;
    }

    /**
     * 当前写入 Codec，版本与 Java 类型不在 Setting 中重复声明。
     *
     * @return 当前写入 Codec，版本与 Java 类型不在 Setting 中重复声明
     */
    public @NotNull VersionedCodec<T, ?> codec() {
        return codec;
    }

    /**
     * 是否要求持久化实现加密保存。
     *
     * @return 是否要求持久化实现加密保存
     */
    public boolean secret() {
        return secret;
    }

    /** 返回固定 owner；未声明时为空，每次调用必须提供 owner。 */
    public @Nullable SettingOwner fixedOwner() {
        return owner == null ? null : owner.get();
    }

    /** 返回默认值副本；必需设置没有默认值。 */
    public @NotNull T defaultValue() {
        if (defaultValue == null)
            throw new IllegalStateException("设置没有默认值: " + code);
        return copy.apply(defaultValue);
    }

    /** 读取固定 owner 的值。 */
    public @NotNull T get() {
        return get(requireOwner());
    }

    /** 读取指定 owner 的有效值。 */
    public @NotNull T get(@NotNull SettingOwner owner) {
        return inspect(owner).value();
    }

    /** 返回固定 owner 的值和读取元数据。 */
    public @NotNull SettingValue<T> inspect() {
        return inspect(requireOwner());
    }

    /** 只有缺失才读取下一层；基础值及所有覆盖完成后校验完整结果。 */
    public @NotNull SettingValue<T> inspect(@NotNull SettingOwner owner) {
        checkOwner(owner);
        List<SettingValue<?>> reads = new ArrayList<>();
        T value = null;
        // 每个来源先按保存的 Codec 版本解码；坏数据直接失败，不能降级为默认值。
        for (var reader : readers) {
            SettingValue<T> read = reader.apply(code, owner);
            reads.add(read);
            if (read.present()) {
                value = read.value();
                break;
            }
        }
        if (value == null)
            value = defaultValue();
        // 覆盖数据使用自己的 Codec，App 显式解释各字段的 Patch。SettingOwner
        for (Overlay<T> overlay : overlays)
            value = overlay.apply(code, owner, value, reads);
        return SettingValue.resolved(code, owner, validate(value), codec.version(), reads);
    }

    /** 对完整值执行类型检查和领域校验。 */
    public final @NotNull T validate(@NotNull T value) {
        if (!codec.valueType().isInstance(value))
            throw new IllegalArgumentException("设置值类型不一致: " + code);
        return copy.apply(validation.run(copy.apply(value)));
    }

    /** 拒绝把固定 owner 的设置当成其他所属访问。 */
    protected final void checkOwner(@NotNull SettingOwner owner) {
        Objects.requireNonNull(owner);
        SettingOwner fixed = fixedOwner();
        if (fixed != null && !fixed.equals(owner))
            throw new IllegalArgumentException("设置 owner 与声明不一致: " + code);
    }

    /** 返回明确固定的 owner，不从线程或全局身份猜测。 */
    protected final @NotNull SettingOwner requireOwner() {
        SettingOwner fixed = fixedOwner();
        if (fixed == null)
            throw new IllegalStateException("设置必须显式提供 owner: " + code);
        return fixed;
    }

    /** 保存读取覆盖数据并合并的内部操作，不作为独立领域 API。 */
    @FunctionalInterface
    private interface Overlay<T> {
        /** 将一次覆盖及其读取元数据追加到当前结果。 */
        T apply(TreeCode code, SettingOwner owner, T value, List<SettingValue<?>> reads);
    }

    /** 在构造阶段声明 IO 与解码顺序；构造后只通过 Setting 访问。 */
    public static final class Builder<T, D> {
        private final TreeCode code;

        private final VersionedCodec<T, D> current;

        private final List<VersionedCodec<T, D>> versions = new ArrayList<>();

        private final Function<T, T> copy;

        private Pipeline<T, T> validation;

        private T defaultValue;

        private java.util.function.Supplier<SettingOwner> owner;

        private boolean secret;

        private boolean frozen;

        private Function<Builder<T, D>, WritableSetting<T>> writable;

        private final List<BiFunction<TreeCode, SettingOwner, SettingValue<T>>> readers = new ArrayList<>();

        private final List<Overlay<T>> overlays = new ArrayList<>();

        private Builder(TreeCode code, VersionedCodec<T, D> codec) {
            this.code = Objects.requireNonNull(code);
            if (code.hasNode(TreeCode.of("*")))
                throw new IllegalArgumentException("设置编码不能包含通配符");
            current = Objects.requireNonNull(codec);
            versions.add(codec);
            new VersionedCodecCatalog<>(codec, versions);
            copy = value -> codec.decode(codec.encode(value));
            validation = Pipeline.identity(codec.valueType());
        }

        /** 登记同一领域类型的历史读取 Codec；当前版本始终用于写入。 */
        public @NotNull Builder<T, D> readVersion(@NotNull VersionedCodec<T, D> codec) {
            mutable();
            versions.add(Objects.requireNonNull(codec));
            return this;
        }

        /** 追加与领域外部表示相同的读取器。 */
        public @NotNull Builder<T, D> from(@NotNull SettingReader<D> reader) {
            mutable();
            readers.add((code, owner) -> decode(reader.read(code, owner), Function.identity()));
            return this;
        }

        /** 先转换文件等内容格式，再选择领域版本 Codec。 */
        public <W> @NotNull Builder<T, D> from(@NotNull SettingReader<W> reader, @NotNull Codec<D, W> format) {
            mutable();
            readers.add((code, owner) -> decode(reader.read(code, owner), format::decode));
            return this;
        }

        /** 使用明确的继承 owner，来源顺序仍由声明位置决定。 */
        public @NotNull Builder<T, D> inherit(@NotNull SettingReader<D> reader, @NotNull SettingOwner inherited) {
            mutable();
            readers.add((code, owner) -> decode(reader.read(code, inherited), Function.identity()));
            return this;
        }

        /** 覆盖输入是普通数据；字段 IGNORE、SET、CLEAR 由明确的合并函数解释。 */
        public <P, W> @NotNull Builder<T, D> overlay(
                @NotNull SettingReader<W> reader,
                @NotNull VersionedCodec<P, W> patchCodec,
                @NotNull BiFunction<P, T, T> merge) {
            mutable();
            overlays.add((code, owner, value, reads) -> {
                SettingValue<W> raw = reader.read(code, owner);
                reads.add(raw);
                if (!raw.present())
                    return value;
                if (raw.codecVersion() != 0 && raw.codecVersion() != patchCodec.version()) {
                    throw new IllegalArgumentException("覆盖内容 Codec 版本不受支持");
                }
                return Objects.requireNonNull(merge.apply(patchCodec.decode(raw.value()), copy.apply(value)));
            });
            return this;
        }

        /** 声明最低优先级默认值；未声明则为必需设置。 */
        public @NotNull Builder<T, D> defaultValue(@NotNull T value) {
            mutable();
            defaultValue = Objects.requireNonNull(value);
            return this;
        }

        /** 声明完整值校验。 */
        public @NotNull Builder<T, D> validate(@NotNull Pipeline<T, T> pipeline) {
            mutable();
            validation = Objects.requireNonNull(pipeline);
            return this;
        }

        /** 声明持久化敏感值。 */
        public @NotNull Builder<T, D> secret(boolean sensitive) {
            mutable();
            secret = sensitive;
            return this;
        }

        /** 固定本设置的 owner，空值表示调用时必须显式提供。 */
        public @NotNull Builder<T, D> owner(@Nullable SettingOwner owner) {
            mutable();
            this.owner = owner == null ? null : () -> owner;
            return this;
        }

        /** 声明每次读取都从同一全局配置取得所属，例如运行时可配置的 `Subject.SYSTEM`。 */
        public @NotNull Builder<T, D> owner(@Nullable java.util.function.Supplier<SettingOwner> owner) {
            mutable();
            this.owner = owner;
            return this;
        }

        /** 选择同时支持读写的目标；读取用于取得目标自己的条件版本。 */
        public <A extends SettingReader<D> & SettingWriter<D>> @NotNull Builder<T, D> writeTo(@NotNull A access) {
            mutable();
            writable = builder -> new WritableSetting<>(builder, access, access, Function.identity());
            return this;
        }

        /** 用指定外部格式保存到明确的可读写目标。 */
        public <W, A extends SettingReader<W> & SettingWriter<W>> @NotNull Builder<T, D> writeTo(
                @NotNull A access,
                @NotNull Codec<D, W> format) {
            mutable();
            writable = builder -> new WritableSetting<>(builder, access, access, format::encode);
            return this;
        }

        /** 创建只读设置，历史 Codec 目录必须完整且无冲突。 */
        public @NotNull Setting<T> build() {
            mutable();
            catalog();
            return new Setting<>(this);
        }

        /** 创建可写设置；没有写入目标时失败。 */
        public @NotNull WritableSetting<T> buildWritable() {
            mutable();
            catalog();
            if (writable == null)
                throw new IllegalStateException("可写设置必须声明写入目标");
            return writable.apply(this);
        }

        /** 返回当前领域编码器，供可写设置捕获强类型的编码函数。 */
        VersionedCodec<T, D> current() {
            return current;
        }

        /** 拒绝构造后继续修改规则。 */
        private void mutable() {
            if (frozen)
                throw new IllegalStateException("Setting 已构造，不能修改规则");
        }

        /** 验证目录内编码和类型一致，并拒绝重复版本。 */
        private VersionedCodecCatalog<T, D> catalog() {
            return new VersionedCodecCatalog<>(current, versions);
        }

        /** 没有外部版本时使用声明的当前 Codec，不凭空给 ENV 分配存储版本。 */
        private <W> SettingValue<T> decode(SettingValue<W> raw, Function<W, D> format) {
            if (!raw.code().equals(code))
                throw new IllegalArgumentException("读取结果设置编码不一致");
            int version = raw.codecVersion() == 0 ? current.version() : raw.codecVersion();
            return raw.map(value -> catalog().select(version).decode(format.apply(value)));
        }
    }
}
