package com.sca4d.core.api.i18n;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;

/** 按消息键和 Locale 读取模块提供的静态消息。 */
@FunctionalInterface
public interface MessageCatalog {

    /**
     * 读取并格式化静态消息。
     *
     * @param key 稳定消息键
     * @param locale 要读取的 Locale
     * @param arguments 消息模板参数
     * @return 找到消息时返回格式化结果，否则返回空
     */
    @NotNull
    Optional<String> find(
            @NotNull String key,
            @NotNull Locale locale,
            @NotNull Object... arguments);
}
