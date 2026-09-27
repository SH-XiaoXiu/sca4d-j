package com.sca4d.core.api.dictionary;

import com.sca4d.core.api.i18n.LocalizableText;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.api.i18n.LocalizedFieldsSchema;
import com.sca4d.core.api.i18n.MessageText;
import com.sca4d.core.api.i18n.TranslatedText;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 保存字典查询返回的一个代码选项或数据库选项。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class DictionaryOption {

    private static final LocalizedFieldsSchema I18N_SCHEMA = new LocalizedFieldsSchema(
            Set.of("label"),
            Set.of("label"),
            Map.of("label", 160));

    private final String value;

    private final String labelKey;

    private final String label;

    private final LocalizedFields i18n;

    private final boolean codeDefined;

    private final boolean enabled;

    private final int sortOrder;

    /**
     * 创建字典选项。
     *
     * @param value 提交给业务接口的值
     * @param labelKey 代码选项的国际化消息键；数据库选项为空
     * @param label 数据库维护的展示文本；代码选项为空
     * @param i18n 数据库选项按 Locale 保存的 label；代码选项为空，可以不包含任何 Locale
     * @param codeDefined 是否由 Java 枚举声明
     * @param enabled 是否允许用于新的业务输入
     * @param sortOrder 同一字典内的展示顺序
     */
    public DictionaryOption(
            @NotNull String value,
            @Nullable String labelKey,
            @Nullable String label,
            @Nullable LocalizedFields i18n,
            boolean codeDefined,
            boolean enabled,
            int sortOrder) {
        this.value = TextUtils.requireText(value, "字典值");
        this.labelKey = optionalText(labelKey, "字典标签消息键");
        this.label = optionalText(label, "字典标签");
        this.i18n = i18n == null ? null : I18N_SCHEMA.validate(i18n);
        this.codeDefined = codeDefined;
        this.enabled = enabled;
        this.sortOrder = sortOrder;
        boolean invalidCodeOption = codeDefined
                && (this.labelKey == null || this.label != null || this.i18n != null);
        boolean invalidDatabaseOption = !codeDefined
                && (this.labelKey != null || this.label == null || this.i18n == null);
        if (invalidCodeOption || invalidDatabaseOption) {
            throw new IllegalArgumentException("代码字典项必须使用 labelKey，数据库字典项必须使用 label 和 i18n");
        }
    }

    /** @return 提交给业务接口的值 */
    @Contract(pure = true)
    public @NotNull String value() {
        return value;
    }

    /** @return 代码选项的国际化消息键；数据库选项返回空 */
    @Contract(pure = true)
    public @Nullable String labelKey() {
        return labelKey;
    }

    /** @return 数据库维护的展示文本；代码选项返回空 */
    @Contract(pure = true)
    public @Nullable String label() {
        return label;
    }

    /** @return 数据库选项的完整多语言 label；代码选项返回空 */
    @Contract(pure = true)
    public @Nullable LocalizedFields i18n() {
        return i18n;
    }

    /** @return 可以按请求 Locale 解析的代码消息或数据库标签 */
    @Contract(pure = true)
    public @NotNull LocalizableText labelText() {
        if (codeDefined) {
            return MessageText.of(Objects.requireNonNull(labelKey));
        }
        Map<Locale, String> translations = new LinkedHashMap<>();
        Objects.requireNonNull(i18n)
                .locales()
                .forEach(
                        locale -> i18n.find(locale, "label")
                                .ifPresent(text -> translations.put(locale, text)));
        return TranslatedText.of(translations, Objects.requireNonNull(label));
    }

    /** @return 由 Java 枚举声明时返回 {@code true} */
    @Contract(pure = true)
    public boolean codeDefined() {
        return codeDefined;
    }

    /** @return 允许用于新的业务输入时返回 {@code true} */
    @Contract(pure = true)
    public boolean enabled() {
        return enabled;
    }

    /** @return 同一字典内的展示顺序 */
    @Contract(pure = true)
    public int sortOrder() {
        return sortOrder;
    }

    /** 校验可空展示文本并清除首尾空白。 */
    private static @Nullable String optionalText(@Nullable String value, @NotNull String name) {
        if (value == null) {
            return null;
        }
        return TextUtils.requireText(Objects.requireNonNull(value), name);
    }
}
