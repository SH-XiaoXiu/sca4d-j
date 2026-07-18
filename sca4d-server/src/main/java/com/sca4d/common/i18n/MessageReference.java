package com.sca4d.common.i18n;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * 尚未绑定请求语言的固定消息引用。
 *
 * <p>业务异常、校验和审计可以保存该对象，HTTP、STOMP 或管理端读取时再使用 {@link LocalizedMessages} 解析最终文本。参数保持
 * 用户原文，不自动翻译；调用方不得放入密码、Token、技术异常文本或其他秘密。
 */
public final class MessageReference {

    private final String key;
    private final List<Object> arguments;

    /**
     * 创建固定消息引用。
     *
     * @param key 在全部支持语言资源中存在的固定消息键
     * @param arguments 消息模板参数
     */
    public MessageReference(@NotNull String key, @NotNull List<Object> arguments) {
        if (key.isBlank()) {
            throw new IllegalArgumentException("消息键不能为空");
        }
        this.key = key;
        this.arguments = List.copyOf(arguments);
    }

    /**
     * 从消息资源键和格式化参数创建消息引用。
     *
     * @param key 在全部支持语言资源中存在的固定消息键
     * @param arguments 消息模板参数
     * @return 保持参数顺序的不可变消息引用
     */
    public static @NotNull MessageReference of(
            @NotNull String key, @NotNull Object... arguments) {
        return new MessageReference(key, Arrays.asList(arguments.clone()));
    }

    /** @return 在全部支持语言资源中存在的固定消息键 */
    public @NotNull String getKey() {
        return key;
    }

    /** @return 保持模板顺序的不可变消息参数 */
    public @NotNull List<Object> getArguments() {
        return arguments;
    }
}
