package com.sca4d.core.api.i18n;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** 保存需要由 MessageCatalog 解析的静态消息键和模板参数。 */
public class MessageText extends LocalizableText {

    private final List<Object> arguments;

    private final String key;

    /** 创建静态消息文本。 */
    private MessageText(String key, Object[] arguments) {
        this.key = requireText(key);
        Objects.requireNonNull(arguments, "消息参数不能为空");
        this.arguments = List.copyOf(Arrays.asList(arguments.clone()));
        if (this.arguments.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("消息参数不能包含 null");
        }
    }

    /**
     * 创建静态消息文本。
     *
     * @param key 跨版本保持不变的消息键
     * @param arguments 消息模板参数
     * @return 新文本
     */
    @CheckReturnValue
    public static @NotNull MessageText of(@NotNull String key, @NotNull Object... arguments) {
        return new MessageText(key, arguments);
    }

    /**
     * MessageCatalog 查找模板时使用的键。
     *
     * @return 跨版本保持不变的消息键
     */
    public @NotNull String key() {
        return key;
    }

    /**
     * 解析消息模板时按位置传入的参数。
     *
     * @return 保持输入顺序的不可变消息模板参数
     */
    public @Unmodifiable @NotNull List<Object> arguments() {
        return arguments;
    }

    /** 校验必须包含可见字符的文本。 */
    private static String requireText(String value) {
        Objects.requireNonNull(value, "消息键不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException("消息键不能为空白");
        }
        return value;
    }
}
