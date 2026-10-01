package com.sca4d.core.api.i18n;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** 保存应用支持的 Locale 及默认 Locale，并按配置顺序选择请求语言。 */
@SuppressWarnings({"ClassCanBeRecord", "unused"})
public class LocaleCatalog {

    private final List<Locale> supportedLocales;

    private final Locale defaultLocale;

    /**
     * 创建 Locale 目录。
     *
     * @param supportedLocales 应用支持的 Locale；顺序用于同语言回退
     * @param defaultLocale 请求语言不受支持时使用的 Locale，必须包含在支持列表中
     */
    public LocaleCatalog(@NotNull List<Locale> supportedLocales, @NotNull Locale defaultLocale) {
        Objects.requireNonNull(supportedLocales, "支持的 Locale 不能为空");
        this.defaultLocale = normalize(Objects.requireNonNull(defaultLocale, "默认 Locale 不能为空"));
        Set<Locale> normalized = new LinkedHashSet<>();
        for (Locale locale : supportedLocales) {
            Locale value = normalize(Objects.requireNonNull(locale, "支持的 Locale 不能包含 null"));
            if (!normalized.add(value)) {
                throw new IllegalArgumentException("重复支持 Locale: " + value.toLanguageTag());
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个支持的 Locale");
        }
        if (!normalized.contains(this.defaultLocale)) {
            throw new IllegalArgumentException(
                    "默认 Locale 必须包含在支持列表中: "
                            + this.defaultLocale.toLanguageTag());
        }
        this.supportedLocales = List.copyOf(normalized);
    }

    /**
     * 应用接受的 Locale 及同语言回退次序。
     *
     * @return 保持配置顺序的不可变支持列表
     */
    public @Unmodifiable @NotNull List<Locale> supportedLocales() {
        return supportedLocales;
    }

    /**
     * 所有请求语言都不受支持时使用的 Locale。
     *
     * @return 支持列表中包含的应用默认 Locale
     */
    public @NotNull Locale defaultLocale() {
        return defaultLocale;
    }

    /**
     * 按精确匹配、同语言首个配置项、默认 Locale 的顺序选择 Locale。
     *
     * @param requested 请求 Locale
     * @return 应用支持的 Locale
     */
    public @NotNull Locale resolve(@NotNull Locale requested) {
        Locale normalized = normalize(Objects.requireNonNull(requested, "请求 Locale 不能为空"));
        for (Locale supported : supportedLocales) {
            if (supported.equals(normalized)) {
                return supported;
            }
        }
        for (Locale supported : supportedLocales) {
            if (supported.getLanguage().equals(normalized.getLanguage())) {
                return supported;
            }
        }
        return defaultLocale;
    }

    /**
     * 按请求优先级选择第一个可用 Locale。
     *
     * @param requestedLocales 已按 Accept-Language 权重排序的 Locale
     * @return 应用支持的 Locale；请求列表为空时返回默认 Locale
     */
    public @NotNull Locale resolve(@NotNull List<Locale> requestedLocales) {
        Objects.requireNonNull(requestedLocales, "请求 Locale 列表不能为空");
        for (Locale requested : requestedLocales) {
            Objects.requireNonNull(requested, "请求 Locale 列表不能包含 null");
            Locale normalized = normalize(requested);
            for (Locale supported : supportedLocales) {
                if (supported.equals(normalized)) {
                    return supported;
                }
            }
            for (Locale supported : supportedLocales) {
                if (supported.getLanguage().equals(normalized.getLanguage())) {
                    return supported;
                }
            }
        }
        return defaultLocale;
    }

    /** 只保留经 Java Locale 往返转换后仍包含语言的标签。 */
    private static Locale normalize(Locale locale) {
        Locale normalized = Locale.forLanguageTag(locale.toLanguageTag());
        if (normalized.getLanguage().isBlank()) {
            throw new IllegalArgumentException("Locale 必须包含语言: " + locale);
        }
        return normalized;
    }
}
