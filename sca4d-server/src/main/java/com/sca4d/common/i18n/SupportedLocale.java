package com.sca4d.common.i18n;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** 后端接口、校验、目录和审计共用支持的语言集合。 */
public enum SupportedLocale implements BaseEnum {
    /** 简体中文，作为系统默认和最终回退语言。 */
    ZH_CN("zh-CN", "简体中文", Locale.SIMPLIFIED_CHINESE, true, true),
    /** 美式英语。 */
    EN_US("en-US", "English", Locale.US, false, false);

    private final String value;
    private final String description;
    private final Locale locale;
    private final boolean defaultLanguage;
    private final boolean required;

    SupportedLocale(
            @NotNull String value,
            @NotNull String description,
            @NotNull Locale locale,
            boolean defaultLanguage,
            boolean required) {
        this.value = value;
        this.description = description;
        this.locale = locale;
        this.defaultLanguage = defaultLanguage;
        this.required = required;
    }

    /**
     * 获取 Spring 国际化公共组件使用的 Locale。
     *
     * @return 与固定语言标签对应的 JDK Locale
     */
    public @NotNull Locale locale() {
        return locale;
    }

    /**
     * 判断当前语言是否承担系统默认和最终回退含义。
     *
     * @return 当前语言是唯一默认语言时返回 {@code true}
     */
    public boolean isDefaultLanguage() {
        return defaultLanguage;
    }

    /**
     * 判断管理端多语言内容是否必须维护当前语言。
     *
     * @return 菜单、字典等通用本地化内容必须提供当前语言时返回 {@code true}
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * 获取系统唯一默认语言。
     *
     * @return 用于未知语言回退的默认语言
     * @throws IllegalStateException 目录没有且仅有一个默认语言时抛出
     */
    public static @NotNull SupportedLocale defaultLocale() {
        List<SupportedLocale> defaults = Arrays.stream(values())
                .filter(SupportedLocale::isDefaultLanguage)
                .toList();
        if (defaults.size() != 1) {
            throw new IllegalStateException("支持语言目录必须且只能声明一个默认语言");
        }
        return defaults.getFirst();
    }

    /**
     * 获取全部支持语言的 JDK 表达。
     *
     * @return 保持产品支持顺序的不可变 Locale 集合
     */
    public static @NotNull List<Locale> locales() {
        return Arrays.stream(values()).map(SupportedLocale::locale).toList();
    }

    /**
     * 判断 BCP 47 语言标签是否属于项目共用支持集合。
     *
     * @param languageTag 字典、请求或配置提供的语言标签
     * @return 与任一固定语言值精确匹配时返回 {@code true}
     */
    public static boolean supports(@NotNull String languageTag) {
        return Arrays.stream(values()).anyMatch(locale -> locale.value.equals(languageTag));
    }

    @Override
    public @NotNull String value() {
        return value;
    }

    @Override
    public @NotNull String description() {
        return description;
    }
}
