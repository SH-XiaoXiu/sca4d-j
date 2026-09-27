package com.sca4d.core.api.i18n;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 保存一行业务数据中按 Locale 和字段键组织的不可变多语言文本。 */
public class LocalizedFields {

    private final Map<Locale, Map<String, String>> values;

    /**
     * 从 JSON 对象使用的 BCP 47 语言标签创建多语言字段。
     *
     * @param values 语言标签到字段文本的两级映射
     * @return 已规范化 Locale 并复制内容的新对象
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    @CheckReturnValue
    public static @NotNull LocalizedFields fromJson(
            @NotNull Map<@NotNull String, @NotNull Map<@NotNull String, @NotNull String>> values) {
        Objects.requireNonNull(values, "多语言字段不能为空");
        Map<Locale, Map<String, String>> localized = new LinkedHashMap<>();
        values.forEach((languageTag, fields) -> {
            Locale locale = normalize(Objects.requireNonNull(languageTag, "Locale 标签不能为空"));
            if (localized.putIfAbsent(locale, fields) != null) {
                throw new IllegalArgumentException("重复 Locale: " + locale.toLanguageTag());
            }
        });
        return new LocalizedFields(localized);
    }

    /**
     * 从 Locale 到字段文本的两级映射创建多语言字段。
     *
     * @param values Locale 到字段文本的两级映射
     * @return 已规范化 Locale 并复制内容的新对象
     */
    @CheckReturnValue
    public static @NotNull LocalizedFields of(
            @NotNull Map<@NotNull Locale, @NotNull Map<@NotNull String, @NotNull String>> values) {
        return new LocalizedFields(values);
    }

    /** 复制并检查全部 Locale、字段键和文本。 */
    private LocalizedFields(Map<Locale, ? extends Map<String, String>> values) {
        Objects.requireNonNull(values, "多语言字段不能为空");
        Map<Locale, Map<String, String>> copied = new LinkedHashMap<>();
        values.forEach((locale, fields) -> {
            Locale checkedLocale = normalize(Objects.requireNonNull(locale, "Locale 不能为空"));
            Map<String, String> checkedFields = new LinkedHashMap<>();
            Objects.requireNonNull(fields, "Locale 字段不能为空").forEach((key, text) -> {
                String checkedKey = Objects.requireNonNull(key, "国际化字段键不能为空");
                if (checkedKey.isBlank()) {
                    throw new IllegalArgumentException("国际化字段键不能为空白");
                }
                String checkedText = Objects.requireNonNull(text, "国际化字段文本不能为空");
                if (checkedText.isBlank()) {
                    throw new IllegalArgumentException(
                            "国际化字段文本不能为空白: "
                                    + checkedLocale.toLanguageTag() + "/" + checkedKey);
                }
                checkedFields.put(checkedKey, checkedText);
            });
            if (copied.putIfAbsent(
                    checkedLocale,
                    Collections.unmodifiableMap(new LinkedHashMap<>(checkedFields))) != null) {
                throw new IllegalArgumentException("重复 Locale: " + checkedLocale.toLanguageTag());
            }
        });
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(copied));
    }

    /**
     * 读取已经提供内容的 Locale。
     *
     * @return 保持输入顺序的不可修改 Locale 集合
     */
    @Contract(pure = true)
    public @NotNull @Unmodifiable Set<@NotNull Locale> locales() {
        return values.keySet();
    }

    /**
     * 查找指定 Locale 的字段文本。
     *
     * @param locale 已规范化或可规范化的 Locale
     * @param key 字段键
     * @return 匹配文本；Locale 或字段不存在时为空
     */
    @Contract(pure = true)
    public @NotNull Optional<String> find(@NotNull Locale locale, @NotNull String key) {
        Map<String, String> fields = values.get(normalize(Objects.requireNonNull(locale, "Locale 不能为空")));
        return fields == null
                ? Optional.empty()
                : Optional.ofNullable(fields.get(Objects.requireNonNull(key, "国际化字段键不能为空")));
    }

    /**
     * 读取指定 Locale 已保存的全部字段。
     *
     * @param locale 已规范化或可规范化的 Locale
     * @return 不可修改字段映射；Locale 不存在时返回空映射
     */
    @Contract(pure = true)
    public @NotNull @Unmodifiable Map<@NotNull String, @NotNull String> fields(
            @NotNull Locale locale) {
        Map<String, String> fields = values.get(normalize(Objects.requireNonNull(locale, "Locale 不能为空")));
        return fields == null ? Map.of() : fields;
    }

    /**
     * 转换成 JSON 请求、响应和 JSONB 使用的 BCP 47 语言标签映射。
     *
     * @return 保持 Locale 与字段输入顺序的不可修改两级映射
     */
    @JsonValue
    @Contract(pure = true)
    public @NotNull @Unmodifiable Map<@NotNull String, @NotNull Map<@NotNull String, @NotNull String>> toJson() {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        values.forEach((locale, fields) -> result.put(locale.toLanguageTag(), fields));
        return Collections.unmodifiableMap(result);
    }

    /** 把 Locale 规范为稳定的 BCP 47 表示，并拒绝不含语言的值。 */
    private static @NotNull Locale normalize(@NotNull Locale locale) {
        Locale normalized = Locale.forLanguageTag(locale.toLanguageTag());
        if (normalized.getLanguage().isBlank()) {
            throw new IllegalArgumentException("Locale 必须包含语言: " + locale);
        }
        return normalized;
    }

    /** 把文本语言标签转换为稳定 Locale。 */
    private static @NotNull Locale normalize(@NotNull String languageTag) {
        if (languageTag.isBlank()) {
            throw new IllegalArgumentException("Locale 标签不能为空白");
        }
        return normalize(Locale.forLanguageTag(languageTag));
    }
}
