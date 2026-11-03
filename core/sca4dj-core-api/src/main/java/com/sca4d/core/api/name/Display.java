package com.sca4d.core.api.name;

import com.sca4d.core.api.name.code.Code;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 保存一个被声明对象向用户显示的两个国际化消息键：名称消息键和说明消息键。
 *
 * <p>{@code Display} 只保存键，不保存文本；文本放在 i18n 资源文件中，由调用方按当前语言查找。
 * 对象的身份仍由它的 {@link Code} 决定，{@code Display} 只回答「界面上叫什么、怎么解释」。
 *
 * <p>说明消息键固定为「名称消息键 + {@code .description}」，没有单独指定说明键的入口。
 *
 * <p>名称消息键有两种来源：
 * <ul>
 * <li>{@link #of(String, Code)} 按 {@link Code} 推导：前缀，加上编码每个节点转小写、{@code _} 换成 {@code -}
 * 后用 {@code .} 连接，通配节点 {@code *} 写成 {@code all}。例如前缀 {@code order} 与编码
 * {@code ORDERS:INVOICE_LINES:READ} 得到 {@code order.orders.invoice-lines.read}。</li>
 * <li>{@link #of(String)} 直接指定名称消息键，用于编码推导结果与已有资源键不一致的例外。</li>
 * </ul>
 */
public class Display {

    /** 说明消息键后缀。 */
    private static final @NotNull String DESCRIPTION_SUFFIX = ".description";

    private final @NotNull String nameKey;

    private Display(@NotNull String nameKey) {
        this.nameKey = TextUtils.requireText(nameKey, "名称消息键");
    }

    /**
     * 直接指定名称消息键，说明消息键为它加上 {@code .description}。
     *
     * @param nameKey 名称消息键，不能为空白
     * @return 使用该名称消息键的 {@code Display}
     * @throws IllegalArgumentException 名称消息键为空白时抛出
     * @throws NullPointerException     名称消息键为 {@code null} 时抛出
     */
    @CheckReturnValue
    @Contract("_ -> new")
    public static @NotNull Display of(@NotNull String nameKey) {
        return new Display(nameKey);
    }

    /**
     * 按 {@link Code} 推导名称消息键，推导规则见类说明。
     *
     * @param prefix 名称消息键前缀，不含结尾的 {@code .}，不能为空白
     * @param code   被声明对象的编码
     * @return 名称消息键由前缀和编码推导的 {@code Display}
     * @throws IllegalArgumentException 前缀为空白时抛出
     * @throws NullPointerException     前缀或编码为 {@code null} 时抛出
     */
    @CheckReturnValue
    @Contract("_, _ -> new")
    public static @NotNull Display of(@NotNull String prefix, @NotNull Code code) {
        String checkedPrefix = TextUtils.requireText(prefix, "名称消息键前缀");
        String path = Objects.requireNonNull(code, "编码不能为空")
                .nodes()
                .stream()
                .map(node -> node.equals("*") ? "all" : node.toLowerCase(Locale.ROOT).replace('_', '-'))
                .collect(Collectors.joining("."));
        return new Display(checkedPrefix + "." + path);
    }

    /**
     * @return 名称消息键
     */
    @Contract(pure = true)
    public @NotNull String nameKey() {
        return nameKey;
    }

    /**
     * @return 说明消息键，即名称消息键加上 {@code .description}
     */
    @Contract(pure = true)
    public @NotNull String descriptionKey() {
        return nameKey + DESCRIPTION_SUFFIX;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other != null && other.getClass() == getClass() && nameKey.equals(((Display) other).nameKey);
    }

    @Override
    public int hashCode() {
        return nameKey.hashCode();
    }

    @Override
    public @NotNull String toString() {
        return nameKey;
    }
}
