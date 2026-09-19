package com.sca4d.core.api.i18n;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** 保存动态数据已经存在的多语言文本及明确的业务回退值。 */
public final class TranslatedText extends LocalizableText {

    private final Map<Locale, String> translations;

    private final String fallback;

    /** 创建动态翻译文本。 */
    private TranslatedText(Map<Locale, String> translations, String fallback) {
        Objects.requireNonNull(translations, "动态翻译不能为空");
        Map<Locale, String> copied = new LinkedHashMap<>();
        translations.forEach((locale, text) -> {
            Locale checkedLocale = Objects.requireNonNull(locale, "动态翻译 Locale 不能为空");
            String checkedText = Objects.requireNonNull(text, "动态翻译文本不能为空");
            if (checkedText.isBlank()) {
                throw new IllegalArgumentException("动态翻译文本不能为空白: " + checkedLocale);
            }
            copied.put(Locale.forLanguageTag(checkedLocale.toLanguageTag()), checkedText);
        });
        this.translations = Map.copyOf(copied);
        this.fallback = Objects.requireNonNull(fallback, "动态翻译业务回退文本不能为空");
    }

    /**
     * 创建动态翻译文本。
     *
     * @param translations Locale 到最终文本的映射
     * @param fallback 请求和默认 Locale 都没有翻译时使用的业务文本
     * @return 新文本
     */
    @CheckReturnValue
    public static @NotNull TranslatedText of(
            @NotNull Map<Locale, String> translations,
            @NotNull String fallback) {
        return new TranslatedText(translations, fallback);
    }

    /**
     * 读取动态数据已经提供的 Locale 文本。
     *
     * @return 构造时复制的不可变翻译映射
     */
    public @Unmodifiable @NotNull Map<Locale, String> translations() {
        return translations;
    }

    /**
     * 读取任何已配置 Locale 都没有翻译时显示的文本。
     *
     * @return 请求和默认 Locale 都缺失时使用的业务回退文本
     */
    public @NotNull String fallback() {
        return fallback;
    }
}
