package com.sca4d.system;

import com.sca4d.common.i18n.SupportedLocale;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** 菜单由管理员维护的完整多语言标题集合。 */
public final class MenuTitles {

    private static final int MAX_LENGTH = 128;

    private final Map<String, String> titles;

    /**
     * 创建只包含已维护支持语言的菜单标题。
     *
     * @param titles BCP 47 语言标签到管理员展示标题的映射
     * @throws IllegalArgumentException 缺少必填语言、包含额外语言或标题过长时抛出
     */
    public MenuTitles(@NotNull Map<String, String> titles) {
        Map<String, String> checked = new LinkedHashMap<>();
        for (SupportedLocale supported : SupportedLocale.values()) {
            String title = titles.get(supported.value());
            String normalized = title == null ? "" : title.trim();
            if (normalized.length() > MAX_LENGTH) {
                throw new IllegalArgumentException("菜单标题长度不能超过 128");
            }
            if (!normalized.isEmpty()) {
                checked.put(supported.value(), normalized);
            } else if (supported.isRequired()) {
                throw new IllegalArgumentException("菜单标题缺少必填语言: " + supported.value());
            }
        }
        if (titles.keySet().stream().anyMatch(language -> !SupportedLocale.supports(language))) {
            throw new IllegalArgumentException("菜单标题包含不支持的语言");
        }
        this.titles = Collections.unmodifiableMap(checked);
    }

    /**
     * 按请求语言解析标题，未知区域语言回退到简体中文。
     *
     * @param locale 当前请求使用的语言
     * @return 当前语言标题或简体中文标题
     */
    public @NotNull String resolve(@NotNull Locale locale) {
        String languageTag = locale.toLanguageTag();
        String exact = titles.get(languageTag);
        if (exact != null) {
            return exact;
        }
        return titles.get(SupportedLocale.defaultLocale().value());
    }

    /** @return 保持支持语言顺序的不可变标题映射 */
    public @NotNull Map<String, String> asMap() {
        return titles;
    }

    /** @return 标题映射相同时返回 true */
    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof MenuTitles that && titles.equals(that.titles);
    }

    /** @return 标题映射的固定哈希值 */
    @Override
    public int hashCode() {
        return Objects.hash(titles);
    }
}
