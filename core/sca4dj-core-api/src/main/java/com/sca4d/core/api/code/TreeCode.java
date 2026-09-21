package com.sca4d.core.api.code;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** 保存忽略大小写、具有稳定层级语义的系统编码。 */
@SuppressWarnings("ClassCanBeRecord") // 项目禁止使用 Java record。
public final class TreeCode {

    private static final @NotNull Pattern SEGMENT = Pattern.compile("[A-Z0-9_]+");

    private final @NotNull String value;

    private final char separator;

    private TreeCode(@NotNull String value, char separator) {
        this.value = value;
        this.separator = separator;
    }

    /**
     * 创建规范化的层级编码。
     *
     * <p>输入可以使用斜杠或冒号，但同一个编码不能混用两种分隔符。规范化只转换大小写，保留实际使用的分隔符。
     *
     * @param value 层级编码
     * @return 已规范化的层级编码
     * @throws NullPointerException value 为空时抛出
     * @throws IllegalArgumentException 编码不符合层级规则时抛出
     */
    @Contract("_ -> new")
    public static @NotNull TreeCode of(@NotNull String value) {
        String checked = Objects.requireNonNull(value, "TreeCode 不能为空").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException("TreeCode 不能为空白");
        }
        String normalized = checked.toUpperCase(Locale.ROOT);
        boolean slash = normalized.indexOf('/') >= 0;
        boolean colon = normalized.indexOf(':') >= 0;
        if (slash == colon) {
            throw new IllegalArgumentException("TreeCode 必须使用一种分隔符: " + value);
        }
        char separator = slash ? '/' : ':';
        String[] segments = normalized.split(Pattern.quote(String.valueOf(separator)), -1);
        if (segments.length < 2) {
            throw new IllegalArgumentException("TreeCode 至少需要两个层级: " + value);
        }
        for (String segment : segments) {
            if (!SEGMENT.matcher(segment).matches()) {
                throw new IllegalArgumentException("TreeCode 层级只能包含大写字母、数字和下划线: " + value);
            }
        }
        return new TreeCode(normalized, separator);
    }

    /** @return 大写的规范化编码，保留创建时使用的分隔符 */
    @Contract(pure = true)
    public @NotNull String value() {
        return value;
    }

    /** @return 当前编码使用的层级分隔符 */
    @Contract(pure = true)
    public char separator() {
        return separator;
    }

    @Override
    @Contract(value = "null -> false", pure = true)
    public boolean equals(@Nullable Object other) {
        return this == other || other instanceof TreeCode code && value.equals(code.value);
    }

    @Override
    @Contract(pure = true)
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    @Contract(pure = true)
    public @NotNull String toString() {
        return value;
    }
}
