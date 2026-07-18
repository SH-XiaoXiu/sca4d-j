package com.sca4d.system;

import com.sca4d.common.i18n.SupportedLocale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** 可配置字典名称或字典项标签的不可变多语言集合。 */
public final class DictionaryLabels {

    private final Map<String, String> labels;

    /**
     * 创建已经完成空白清理的多语言标签。
     *
     * @param labels 以标准语言标签索引的用户维护内容
     */
    public DictionaryLabels(@NotNull Map<String, String> labels) {
        Map<String, String> normalized = new LinkedHashMap<>();
        labels.forEach((language, label) -> {
            if (language != null && label != null && !language.isBlank() && !label.isBlank()) {
                normalized.put(language, label);
            }
        });
        this.labels = Collections.unmodifiableMap(normalized);
    }

    /**
     * 按请求语言解析可配置业务标签。
     *
     * <p>用户录入内容不自动翻译。缺少当前语言时依次回退简体中文和第一个已维护标签，最终使用调用方提供的固定文本。
     *
     * @param locale 当前请求语言
     * @param fallback 所有标签均缺失时使用的固定文本
     * @return 可安全展示的用户维护标签
     */
    public @NotNull String resolve(@NotNull Locale locale, @NotNull String fallback) {
        String exact = labels.get(locale.toLanguageTag());
        if (exact != null) {
            return exact;
        }
        String defaultLabel = labels.get(SupportedLocale.ZH_CN.value());
        if (defaultLabel != null) {
            return defaultLabel;
        }
        return labels.values().stream().findFirst().orElse(fallback);
    }

    /**
     * 读取与当前请求语言精确匹配的后台维护标签。
     *
     * <p>代码来源字典用本方法判断后台是否已经设置当前语言；缺失时由调用方使用代码消息键，不选取其他语言内容。
     *
     * @param locale 当前请求语言
     * @return 精确语言标签；未维护时返回 {@code null}
     */
    public @Nullable String find(@NotNull Locale locale) {
        return labels.get(locale.toLanguageTag());
    }

    /** @return 以标准语言标签索引的不可变用户内容 */
    public @NotNull Map<String, String> values() {
        return labels;
    }
}
