package com.sca4d.core.api.name;

import com.sca4d.core.api.isolation.AppName;
import com.sca4d.core.api.name.code.Code;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** 使用稳定 Code 和动态值组成完整 IO 键；动态值保留原始区别，物理输出使用全局 App 隔离。 */
public class Key extends Name {

    private final @NotNull List<String> segments;

    /** 共享不可变键节点，供锁键等专属领域类型构造使用。 */
    protected Key(@NotNull Key key) {
        super(key);
        segments = Objects.requireNonNull(key).segments;
    }

    /** 保存已经编码的逻辑键节点；只由工厂和边界解析使用。 */
    protected Key(@NotNull List<String> segments) {
        super(NameFormat.COLON.join(segments));
        if (segments.isEmpty() || segments.get(0).startsWith("V_") || segments.get(0).matches("-?[0-9]+")) {
            throw new IllegalArgumentException("Key 至少需要一个稳定节点");
        }
        this.segments = List.copyOf(segments);
    }

    /** 用稳定编码构造固定 IO 键，通配编码不能作为实际键。 */
    public static @NotNull Key of(@NotNull Code code) {
        List<String> values = new ArrayList<>();
        for (String node : Objects.requireNonNull(code).nodes()) {
            if (node.equals("*")) {
                throw new IllegalArgumentException("Key 的稳定编码不能包含通配符");
            }
            values.add(stableSegment(node));
        }
        return new Key(values);
    }

    /** 以有序动态文本构造键；文本不按 Code 规则转换大小写。 */
    public static @NotNull Key of(@NotNull Code code, String @NotNull... values) {
        Key key = of(code);
        for (String value : values) {
            key = key.part(value);
        }
        return key;
    }

    /** 以有序动态数字构造键，数字在物理键中使用十进制。 */
    public static @NotNull Key of(@NotNull Code code, long @NotNull... values) {
        Key key = of(code);
        for (long value : values) {
            key = key.part(value);
        }
        return key;
    }

    /** 追加原始动态文本，UTF-8 字节转大写十六进制，空文本保留为独立值。 */
    public @NotNull Key part(@NotNull String value) {
        List<String> values = new ArrayList<>(segments);
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .encode(
                            CharBuffer.wrap(Objects.requireNonNull(value, "Key 动态文本不能为空")));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            values.add("V_" + HexFormat.of().withUpperCase().formatHex(bytes));
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Key 动态文本必须是合法 Unicode", exception);
        }
        return withSegments(values);
    }

    /** 追加动态数字；固定编码中的数字节点使用转义，避免与动态数字混淆。 */
    public @NotNull Key part(long value) {
        List<String> values = new ArrayList<>(segments);
        values.add(Long.toString(value));
        return withSegments(values);
    }

    /** 在动态部分之后追加代码声明的稳定后缀。 */
    public @NotNull Key append(@NotNull Code suffix) {
        List<String> values = new ArrayList<>(segments);
        values.addAll(of(suffix).segments);
        return withSegments(values);
    }

    /** 返回保持当前领域类型的新键；专属子类覆盖此方法。 */
    protected @NotNull Key withSegments(@NotNull List<String> values) {
        return new Key(values);
    }

    /** 返回边界可以直接编码的不可修改逻辑节点，不包含 App 前缀。 */
    public final @NotNull @Unmodifiable List<String> segments() {
        return segments;
    }

    /** 判断键是否由给定稳定根编码开始。 */
    public final boolean isUnder(@NotNull Code root) {
        List<String> prefix = of(root).segments;
        return segments.size() >= prefix.size() && segments.subList(0, prefix.size()).equals(prefix);
    }

    /** 在边界输出带 App 前缀的完整键；逻辑编码自身不追加前缀。 */
    @Override
    public final @NotNull String format(@NotNull NameFormat format) {
        return isolate(format).format(format);
    }

    @Override
    protected final @NotNull String logicalFormat(@NotNull NameFormat format) {
        return Objects.requireNonNull(format).join(segments);
    }

    /** 在 Redis 等边界恢复逻辑键，拒绝跨 App 名称以及非规范键节点。 */
    public static @NotNull Key parse(@NotNull String name, @NotNull NameFormat format) {
        String prefix = AppName.current().format(format) + format.separator();
        if (!Objects.requireNonNull(name).startsWith(prefix)) {
            throw new IllegalArgumentException("Key 的 App 隔离前缀不匹配");
        }
        return fromSegments(
                List.of(
                        name.substring(prefix.length())
                                .split(
                                        Pattern.quote(String.valueOf(format.separator())),
                                        -1)));
    }

    /** 恢复已编码逻辑节点；只供实际序列化边界使用。 */
    public static @NotNull Key fromSegments(@NotNull List<String> segments) {
        for (String segment : segments) {
            if (segment.startsWith("V_")) {
                byte[] bytes = HexFormat.of().parseHex(segment.substring(2));
                String decoded = new String(bytes, StandardCharsets.UTF_8);
                if (!segment.equals(
                        "V_" + HexFormat.of().withUpperCase().formatHex(decoded.getBytes(StandardCharsets.UTF_8)))) {
                    throw new IllegalArgumentException("Key 动态文本编码无效");
                }
            } else if (segment.matches("-?[0-9]+")) {
                if (!segment.equals(Long.toString(Long.parseLong(segment)))) {
                    throw new IllegalArgumentException("Key 动态数字不规范");
                }
            } else {
                String logical = segment.startsWith("C_") ? segment.substring(2) : segment;
                if (!logical.matches("[A-Z0-9_]+") || !segment.equals(stableSegment(logical))) {
                    throw new IllegalArgumentException("Key 稳定节点不规范");
                }
            }
        }
        return new Key(segments);
    }

    /** 对固定节点中的保留标记及数字做可逆转义，区分固定名称和动态值。 */
    private static @NotNull String stableSegment(@NotNull String node) {
        return node.startsWith("V_") || node.startsWith("C_") || node.matches("-?[0-9]+") ? "C_" + node : node;
    }

    @Override
    public final boolean equals(@Nullable Object other) {
        return this == other
                || other instanceof Key key && getClass() == key.getClass() && segments.equals(key.segments);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(getClass(), segments);
    }

    /** 返回不含 App 隔离的逻辑节点，供日志定位；实际 IO 使用 format。 */
    @Override
    public final @NotNull String toString() {
        return NameFormat.COLON.join(segments);
    }
}
