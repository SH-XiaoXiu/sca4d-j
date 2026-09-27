package com.sca4d.core.api.i18n;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 校验一种业务数据允许保存的国际化字段、必填项和最大长度。 */
public class LocalizedFieldsSchema {

    private final Set<String> allowedKeys;

    private final Set<String> requiredKeys;

    private final Map<String, Integer> maximumLengths;

    /**
     * 创建业务国际化字段规则。
     *
     * @param allowedKeys 允许出现在每个 Locale 下的全部字段键
     * @param requiredKeys Locale 一旦出现就必须包含的字段键
     * @param maximumLengths 每个允许字段的最大字符数
     */
    public LocalizedFieldsSchema(
            @NotNull Set<@NotNull String> allowedKeys,
            @NotNull Set<@NotNull String> requiredKeys,
            @NotNull Map<@NotNull String, @NotNull Integer> maximumLengths) {
        this.allowedKeys = copyKeys(allowedKeys, "允许字段键");
        this.requiredKeys = copyKeys(requiredKeys, "必填字段键");
        if (this.allowedKeys.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个允许的国际化字段键");
        }
        if (!this.allowedKeys.containsAll(this.requiredKeys)) {
            throw new IllegalArgumentException("必填国际化字段必须包含在允许字段中");
        }
        Objects.requireNonNull(maximumLengths, "字段最大长度不能为空");
        if (!maximumLengths.keySet().equals(this.allowedKeys)) {
            throw new IllegalArgumentException("每个允许的国际化字段必须声明最大长度");
        }
        maximumLengths.forEach((key, length) -> {
            Objects.requireNonNull(key, "最大长度字段键不能为空");
            if (Objects.requireNonNull(length, "字段最大长度不能为空") <= 0) {
                throw new IllegalArgumentException("字段最大长度必须大于零: " + key);
            }
        });
        this.maximumLengths = Map.copyOf(maximumLengths);
    }

    /**
     * 按当前业务 schema 校验完整多语言内容，不要求必须包含某个 Locale。
     *
     * <p>适用于另有业务回退文本、允许暂时没有翻译的数据。
     *
     * @param fields 待校验的完整多语言内容
     * @return 原始不可变对象
     */
    @CheckReturnValue
    public @NotNull LocalizedFields validate(@NotNull LocalizedFields fields) {
        LocalizedFields checked = Objects.requireNonNull(fields, "多语言字段不能为空");
        validateValues(checked);
        return checked;
    }

    /**
     * 按默认 Locale 和当前业务 schema 校验完整多语言内容。
     *
     * <p>动态数据允许保存任意合法 Locale，不受应用静态消息目录限制。
     *
     * @param fields 待校验的完整多语言内容
     * @param defaultLocale 动态字段回退时必须存在的默认 Locale
     * @return 原始不可变对象
     */
    @CheckReturnValue
    public @NotNull LocalizedFields validate(
            @NotNull LocalizedFields fields,
            @NotNull Locale defaultLocale) {
        LocalizedFields checked = validate(fields);
        Locale fallback = Locale.forLanguageTag(
                Objects.requireNonNull(defaultLocale, "默认 Locale 不能为空").toLanguageTag());
        if (fallback.getLanguage().isBlank()) {
            throw new IllegalArgumentException("默认 Locale 必须包含语言");
        }
        if (!checked.locales().contains(fallback)) {
            throw new IllegalArgumentException(
                    "缺少默认 Locale: "
                            + fallback.toLanguageTag());
        }
        return checked;
    }

    /** 校验每个已提供 Locale 的字段集合和文本长度。 */
    private void validateValues(@NotNull LocalizedFields fields) {
        for (var locale : fields.locales()) {
            Map<String, String> values = fields.fields(locale);
            if (!allowedKeys.containsAll(values.keySet())) {
                Set<String> unknown = new LinkedHashSet<>(values.keySet());
                unknown.removeAll(allowedKeys);
                throw new IllegalArgumentException("包含未声明的国际化字段: " + unknown);
            }
            if (!values.keySet().containsAll(requiredKeys)) {
                Set<String> missing = new LinkedHashSet<>(requiredKeys);
                missing.removeAll(values.keySet());
                throw new IllegalArgumentException("缺少必填国际化字段: " + missing);
            }
            values.forEach((key, text) -> {
                int maximumLength = maximumLengths.get(key);
                if (text.length() > maximumLength) {
                    throw new IllegalArgumentException(
                            "国际化字段超过最大长度: "
                                    + locale.toLanguageTag() + "/" + key);
                }
            });
        }
    }

    /** 复制并检查 schema 字段键。 */
    private static @NotNull Set<String> copyKeys(
            @NotNull Set<@NotNull String> keys,
            @NotNull String label) {
        Set<String> copied = new LinkedHashSet<>();
        for (String key : Objects.requireNonNull(keys, label + "不能为空")) {
            String checked = Objects.requireNonNull(key, label + "不能包含 null");
            if (checked.isBlank()) {
                throw new IllegalArgumentException(label + "不能为空白");
            }
            copied.add(checked);
        }
        return Set.copyOf(copied);
    }
}
