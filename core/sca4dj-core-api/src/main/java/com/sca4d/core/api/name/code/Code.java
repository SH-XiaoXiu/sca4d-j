package com.sca4d.core.api.name.code;

import com.sca4d.core.api.name.Name;
import com.sca4d.core.api.name.NameFormat;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** 保存代码声明的稳定编码节点链；领域类型共享规则并保留自己的类型身份。 */
public abstract class Code extends Name implements Comparable<Code> {

    private final @NotNull Node head;

    private final int size;

    /** 校验并保存稳定节点；不可包含 IO 动态标识。 */
    protected Code(@NotNull List<String> nodes) {
        super(String.join(":", nodes).toUpperCase(Locale.ROOT));
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("Code 至少需要一个节点");
        }
        Node next = null;
        for (int index = nodes.size() - 1; index >= 0; index--) {
            String value = Objects.requireNonNull(nodes.get(index), "Code 节点不能为空").toUpperCase(Locale.ROOT);
            if (!value.matches("[A-Z0-9_-]+|\\*")) {
                throw new IllegalArgumentException("Code 节点只能包含字母、数字、下划线、连字符或完整通配节点");
            }
            next = new Node(value, next);
        }
        head = Objects.requireNonNull(next);
        size = nodes.size();
    }

    /** 共享已校验的不可变节点链，供专属领域编码构造使用。 */
    protected Code(@NotNull Code source) {
        super(source);
        head = Objects.requireNonNull(source).head;
        size = source.size;
    }

    /** 返回不可修改的稳定节点序列。 */
    @Contract(pure = true)
    public final @NotNull @Unmodifiable List<String> nodes() {
        List<String> values = new ArrayList<>(size);
        for (Node node = head; node != null; node = node.next) {
            values.add(node.value);
        }
        return List.copyOf(values);
    }

    /** 返回节点数量，不依赖输出分隔符。 */
    @Contract(pure = true)
    public final int size() {
        return size;
    }

    /** 比较编码内容；调用方明确需要跨领域类型比较时使用。 */
    @Contract(pure = true)
    public final boolean sameCode(@NotNull Code other) {
        return nodes().equals(Objects.requireNonNull(other).nodes());
    }

    @Override
    @Contract(pure = true)
    public final @NotNull String format(@NotNull NameFormat format) {
        return logicalFormat(format);
    }

    @Override
    protected final @NotNull String logicalFormat(@NotNull NameFormat format) {
        return Objects.requireNonNull(format).join(nodes());
    }

    /** 按领域类型和节点内容稳定排序，与 equals 使用相同的身份规则。 */
    @Override
    public final int compareTo(@NotNull Code other) {
        Objects.requireNonNull(other);
        int type = getClass().getName().compareTo(other.getClass().getName());
        if (type != 0) {
            return type;
        }
        List<String> left = nodes();
        List<String> right = other.nodes();
        for (int index = 0; index < Math.min(left.size(), right.size()); index++) {
            int compared = left.get(index).compareTo(right.get(index));
            if (compared != 0) {
                return compared;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    @Override
    @Contract(value = "null -> false", pure = true)
    public final boolean equals(@Nullable Object other) {
        return this == other || other instanceof Code code && getClass() == code.getClass() && sameCode(code);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(getClass(), nodes());
    }

    /** 返回便于日志定位的冒号逻辑编码；业务传递和持久化必须保留 Code 类型或显式选择格式。 */
    @Override
    public final @NotNull String toString() {
        return format(NameFormat.COLON);
    }

    /** 保存一个稳定节点及其后继，不保存任何序列化分隔符。 */
    private static final class Node {
        private final @NotNull String value;

        private final @Nullable Node next;

        private Node(@NotNull String value, @Nullable Node next) {
            this.value = value;
            this.next = next;
        }
    }
}
