package com.sca4d.core.api.i18n;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Objects;

/** 按静态消息目录或动态数据自身的 Locale 把 LocalizableText 转换成最终字符串。 */
public class TextLocalizer {

    private final LocaleCatalog locales;

    private final MessageCatalog messages;

    /**
     * 创建文本本地化入口。
     *
     * @param locales 应用支持和默认 Locale
     * @param messages 静态消息目录
     */
    public TextLocalizer(@NotNull LocaleCatalog locales, @NotNull MessageCatalog messages) {
        this.locales = Objects.requireNonNull(locales, "LocaleCatalog 不能为空");
        this.messages = Objects.requireNonNull(messages, "MessageCatalog 不能为空");
    }

    /**
     * 本地化静态消息或动态翻译。
     *
     * @param text 待本地化文本
     * @param requestedLocale 当前请求 Locale
     * @return 最终字符串；静态消息完全缺失时返回消息键
     */
    @CheckReturnValue
    public @NotNull String localize(
            @NotNull LocalizableText text,
            @NotNull Locale requestedLocale) {
        Objects.requireNonNull(text, "待本地化文本不能为空");
        Locale requested = normalize(Objects.requireNonNull(requestedLocale, "请求 Locale 不能为空"));
        if (text instanceof MessageText message) {
            Locale supported = locales.resolve(requested);
            Object[] arguments = message.arguments().toArray();
            var localized = messages.find(message.key(), supported, arguments);
            if (localized.isPresent() || supported.equals(locales.defaultLocale())) {
                return localized.orElse(message.key());
            }
            return messages.find(message.key(), locales.defaultLocale(), arguments).orElse(message.key());
        }
        if (text instanceof TranslatedText translated) {
            String requestedText = translated.translations().get(requested);
            if (requestedText != null) {
                return requestedText;
            }
            String defaultText = translated.translations().get(locales.defaultLocale());
            return defaultText == null ? translated.fallback() : defaultText;
        }
        throw new IllegalArgumentException("不支持的本地化文本类型: " + text.getClass().getName());
    }

    /**
     * 从动态字段中按请求 Locale 和默认 Locale 的顺序读取最终文本。
     *
     * @param fields 当前业务行保存的完整多语言字段
     * @param key View 声明的字段键
     * @param requestedLocale 当前请求 Locale
     * @return 请求 Locale 或默认 Locale 中保存的文本
     * @throws IllegalArgumentException 请求和默认 Locale 都缺少该字段时抛出
     */
    @CheckReturnValue
    public @NotNull String localize(
            @NotNull LocalizedFields fields,
            @NotNull String key,
            @NotNull Locale requestedLocale) {
        LocalizedFields checkedFields = Objects.requireNonNull(fields, "动态国际化字段不能为空");
        String checkedKey = Objects.requireNonNull(key, "动态国际化字段键不能为空");
        Locale requested = normalize(Objects.requireNonNull(requestedLocale, "请求 Locale 不能为空"));
        return checkedFields.find(requested, checkedKey)
                .or(() -> checkedFields.find(locales.defaultLocale(), checkedKey))
                .orElseThrow(() -> new IllegalArgumentException("动态国际化字段缺少默认文本: " + checkedKey));
    }

    /** 把请求 Locale 规范为动态字段 JSON key 使用的 BCP 47 形式。 */
    private static @NotNull Locale normalize(@NotNull Locale locale) {
        Locale normalized = Locale.forLanguageTag(locale.toLanguageTag());
        if (normalized.getLanguage().isBlank()) {
            throw new IllegalArgumentException("请求 Locale 必须包含语言");
        }
        return normalized;
    }
}
