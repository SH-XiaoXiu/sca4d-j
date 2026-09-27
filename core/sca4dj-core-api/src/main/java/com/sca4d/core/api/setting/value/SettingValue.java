package com.sca4d.core.api.setting.value;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** 保存原始或已解码的读取结果；元数据不持有其他来源的原始内容。 */
public final class SettingValue<T> {
    private final TreeCode code;

    private final SettingOwner owner;

    private final T value;

    private final int codecVersion;

    private final List<Origin> origins;

    private SettingValue(TreeCode code, SettingOwner owner, T value, int codecVersion, List<Origin> origins) {
        this.code = Objects.requireNonNull(code);
        this.owner = Objects.requireNonNull(owner);
        this.value = value;
        if (codecVersion < 0)
            throw new IllegalArgumentException("Codec 版本不能为负数");
        this.codecVersion = codecVersion;
        this.origins = List.copyOf(origins);
    }

    /** 创建一次 IO 读取结果；null 内容表示缺失，codecVersion 为零表示外部表示未携带版本。 */
    public static <T> @NotNull SettingValue<T> read(
            @NotNull TreeCode reader,
            @NotNull TreeCode code,
            @NotNull SettingOwner owner,
            @Nullable T value,
            int codecVersion,
            @Nullable Version revision,
            boolean locked) {
        return new SettingValue<>(
                code,
                owner,
                value,
                codecVersion,
                List.of(new Origin(reader, owner, codecVersion, revision, value != null, locked)));
    }

    /** 创建最终结果，只收集读取元数据；默认值没有来源内容。 */
    public static <T> @NotNull SettingValue<T> resolved(
            @NotNull TreeCode code,
            @NotNull SettingOwner owner,
            @NotNull T value,
            int codecVersion,
            @NotNull List<? extends SettingValue<?>> reads) {
        return new SettingValue<>(
                code,
                owner,
                Objects.requireNonNull(value),
                codecVersion,
                reads.stream().flatMap(read -> read.origins().stream()).toList());
    }

    /** 转换存在的内容并保留 IO 元数据；缺失内容不调用解码器。 */
    public <R> @NotNull SettingValue<R> map(@NotNull Function<? super T, ? extends R> decoder) {
        return new SettingValue<>(
                code,
                owner,
                value == null ? null : Objects.requireNonNull(decoder.apply(value)),
                codecVersion,
                origins);
    }

    /** 返回设置稳定编码。 */
    public @NotNull TreeCode code() {
        return code;
    }

    /** 返回本次请求所属；每个来源的实际所属保存在 origins。 */
    public @NotNull SettingOwner owner() {
        return owner;
    }

    /** 返回存在的值；缺失时明确失败，不返回伪造的默认值。 */
    public @NotNull T value() {
        return Objects.requireNonNull(value, "设置没有覆盖值: " + code);
    }

    /** 返回本结果是否含有值，与版本记录是否存在无关。 */
    public boolean present() {
        return value != null;
    }

    /** 返回内容结构版本；原始无版本来源为零，最终结果使用当前 Codec 版本。 */
    public int codecVersion() {
        return codecVersion;
    }

    /** 返回不包含原始数据的来源说明。 */
    public @NotNull List<Origin> origins() {
        return origins;
    }

    /** 仅单一来源具有存储版本；ENV、JAR 和组合结果不伪造版本。 */
    public @NotNull Optional<Version> revision() {
        return origins.size() == 1 ? Optional.ofNullable(origins.getFirst().revision) : Optional.empty();
    }

    /** 返回是否读取到了持久化或外部覆盖值。 */
    public boolean stored() {
        return origins.stream().anyMatch(Origin::present);
    }

    /** 返回是否有实际覆盖内容被同步规则锁定。 */
    public boolean locked() {
        return origins.stream().anyMatch(origin -> origin.present && origin.locked);
    }

    /** 记录一次实际读取的元数据；IO 身份与请求所属分别保留。 */
    public static final class Origin {
        private final TreeCode reader;

        private final SettingOwner owner;

        private final int codecVersion;

        private final Version revision;

        private final boolean present;

        private final boolean locked;

        private Origin(
                TreeCode reader,
                SettingOwner owner,
                int codecVersion,
                Version revision,
                boolean present,
                boolean locked) {
            this.reader = reader;
            this.owner = owner;
            this.codecVersion = codecVersion;
            this.revision = revision;
            this.present = present;
            this.locked = locked;
        }

        /** 返回读写实现编码。 */
        public @NotNull TreeCode reader() {
            return reader;
        }

        /** 返回实际读取所属。 */
        public @NotNull SettingOwner owner() {
            return owner;
        }

        /** 返回原始内容声明的版本。 */
        public int codecVersion() {
            return codecVersion;
        }

        /** 返回 IO 存储版本；不支持条件写入的来源可为空。 */
        public @NotNull Optional<Version> revision() {
            return Optional.ofNullable(revision);
        }

        /** 返回来源是否包含覆盖内容。 */
        public boolean present() {
            return present;
        }

        /** 返回来源内容是否被同步锁定。 */
        public boolean locked() {
            return locked;
        }
    }
}
