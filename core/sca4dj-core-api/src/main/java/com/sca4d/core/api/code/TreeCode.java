package com.sca4d.core.api.code;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 保存忽略大小写、具有稳定层级语义的系统编码。
 */
@SuppressWarnings("ClassCanBeRecord") // 项目禁止使用 Java record。
public final class TreeCode implements Comparable<TreeCode> {

    private static final @NotNull Pattern SEGMENT = Pattern.compile("[A-Z0-9_]+|\\*");

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
     * @throws NullPointerException     value 为空时抛出
     * @throws IllegalArgumentException 编码不符合层级规则时抛出
     */
    @Contract("_ -> new")
    public static @NotNull TreeCode of(@NotNull String value) {
        return parse(value, null);
    }

    /**
     * 创建使用斜杠分层的规范化编码。
     *
     * @param value 斜杠分层编码
     * @return 已规范化的斜杠分层编码
     * @throws NullPointerException     value 为空时抛出
     * @throws IllegalArgumentException 编码不是斜杠分层编码或不符合层级规则时抛出
     */
    @Contract("_ -> new")
    public static @NotNull TreeCode ofSlash(@NotNull String value) {
        return parse(value, '/');
    }

    /**
     * 创建使用冒号分层的规范化编码。
     *
     * @param value 冒号分层编码
     * @return 已规范化的冒号分层编码
     * @throws NullPointerException     value 为空时抛出
     * @throws IllegalArgumentException 编码不是冒号分层编码或不符合层级规则时抛出
     */
    @Contract("_ -> new")
    public static @NotNull TreeCode ofColon(@NotNull String value) {
        return parse(value, ':');
    }

    /**
     * 创建不带层级分隔符的单段编码，供目录根节点使用。
     */
    @Contract("_ -> new")
    public static @NotNull TreeCode ofSegment(@NotNull String value) {
        String checked = Objects.requireNonNull(value, "TreeCode 段不能为空").strip();
        String normalized = checked.toUpperCase(Locale.ROOT);
        if (!SEGMENT.matcher(normalized).matches()) {
            throw new IllegalArgumentException("TreeCode 段只能包含大写字母、数字和下划线: " + value);
        }
        return new TreeCode(normalized, '\0');
    }

    /**
     * 解析并校验可选的固定分隔符。
     */
    private static @NotNull TreeCode parse(@NotNull String value, @Nullable Character expectedSeparator) {
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
        if (expectedSeparator != null && separator != expectedSeparator) {
            throw new IllegalArgumentException(
                    "TreeCode 必须使用" + (expectedSeparator == '/' ? "斜杠" : "冒号") + "分层: " + value);
        }
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

    /**
     * @return 大写的规范化编码，保留创建时使用的分隔符
     */
    @Contract(pure = true)
    public @NotNull String value() {
        return value;
    }

    /**
     * 判断当前编码是否包含指定的连续节点。
     *
     * <p>单段节点匹配任意层级；多段节点匹配任意连续层级，不要求从当前编码的根节点开始。节点使用的分隔符不影响匹配结果。
     *
     * @param node 要查找的单段或连续节点
     * @return 当前编码包含指定节点时返回 {@code true}
     */
    @Contract(pure = true)
    public boolean hasNode(@NotNull TreeCode node) {
        TreeCode checked = Objects.requireNonNull(node, "TreeCode 节点不能为空");
        String[] current = segments(value, separator);
        String[] expected = segments(checked.value, checked.separator);
        if (expected.length > current.length) {
            return false;
        }
        for (int start = 0; start <= current.length - expected.length; start++) {
            boolean matches = true;
            for (int offset = 0; offset < expected.length; offset++) {
                if (!current[start + offset].equals(expected[offset])) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return 当前编码使用的层级分隔符
     */
    @Contract(pure = true)
    public char separator() {
        return separator;
    }

    /**
     * @return 规范化编码的字符长度
     */
    @Contract(pure = true)
    public int length() {
        return value.length();
    }

    /**
     * 在当前编码后追加一个子节点。
     *
     * @param childSegment 不含层级分隔符的子节点
     * @return 使用当前分隔符的新层级编码
     * @throws IllegalStateException 当前编码是没有指定分隔符的单节点时抛出
     */
    @Contract("_ -> new")
    public @NotNull TreeCode child(@NotNull String childSegment) {
        if (separator == '\0') {
            throw new IllegalStateException("单节点 TreeCode 追加子节点时必须指定分隔符");
        }
        return child(separator, childSegment);
    }

    /**
     * 在当前编码后追加一个已经校验的单节点。
     *
     * @param childNode 不带层级分隔符的子节点
     * @return 使用当前分隔符的新层级编码
     * @throws IllegalStateException 当前编码是没有指定分隔符的单节点时抛出
     */
    @Contract("_ -> new")
    public @NotNull TreeCode child(@NotNull TreeCode childNode) {
        if (separator == '\0') {
            throw new IllegalStateException("单节点 TreeCode 追加子节点时必须指定分隔符");
        }
        return child(separator, childNode);
    }

    /**
     * 使用指定分隔符在当前编码后追加一个子节点。
     *
     * @param childSeparator 斜杠或冒号；已有层级编码必须继续使用原分隔符
     * @param childSegment   不含层级分隔符的子节点
     * @return 追加子节点后的新层级编码
     */
    @Contract("_, _ -> new")
    public @NotNull TreeCode child(char childSeparator, @NotNull String childSegment) {
        return child(childSeparator, ofSegment(childSegment));
    }

    /**
     * 使用指定分隔符在当前编码后追加一个已经校验的单节点。
     *
     * @param childSeparator 斜杠或冒号；已有层级编码必须继续使用原分隔符
     * @param childNode      不带层级分隔符的子节点
     * @return 追加子节点后的新层级编码
     */
    @Contract("_, _ -> new")
    public @NotNull TreeCode child(char childSeparator, @NotNull TreeCode childNode) {
        if (childSeparator != '/' && childSeparator != ':') {
            throw new IllegalArgumentException("TreeCode 子节点分隔符只能是斜杠或冒号");
        }
        if (separator != '\0' && separator != childSeparator) {
            throw new IllegalArgumentException("TreeCode 子节点必须沿用当前分隔符");
        }
        TreeCode child = Objects.requireNonNull(childNode, "TreeCode 子节点不能为空");
        if (child.separator != '\0') {
            throw new IllegalArgumentException("TreeCode 子节点不能包含层级分隔符: " + child);
        }
        return new TreeCode(value + childSeparator + child.value, childSeparator);
    }

    /**
     * 按规范化编码的字典顺序比较，用于稳定排序。
     */
    @Override
    @Contract(pure = true)
    public int compareTo(@NotNull TreeCode other) {
        return value.compareTo(Objects.requireNonNull(other, "TreeCode 不能为空").value);
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

    /**
     * 将编码拆分为不包含分隔符的规范化节点。
     */
    private static @NotNull String[] segments(@NotNull String value, char separator) {
        return separator == '\0' ? new String[] {value} : value.split(Pattern.quote(String.valueOf(separator)), -1);
    }
}
