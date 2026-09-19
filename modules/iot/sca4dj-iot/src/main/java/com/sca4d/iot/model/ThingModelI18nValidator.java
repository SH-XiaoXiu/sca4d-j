package com.sca4d.iot.model;

import com.sca4d.core.api.i18n.LocaleCatalog;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.iot.api.model.ThingModel;
import com.sca4d.iot.api.model.ThingModelPresentation;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 校验物模型动态翻译只引用现有展示路径并完整提供默认 Locale。 */
public final class ThingModelI18nValidator {

    private static final int MAXIMUM_TEXT_LENGTH = 128;

    private final @NotNull LocaleCatalog locales;

    /** @param locales 应用支持和默认 Locale */
    public ThingModelI18nValidator(@NotNull LocaleCatalog locales) {
        this.locales = Objects.requireNonNull(locales, "Locale 目录不能为空");
    }

    /**
     * 校验完整动态翻译。
     *
     * <p>默认 Locale 必须覆盖每个展示路径，其他 Locale 可以只提供需要覆盖的字段。
     *
     * @param model 纯技术物模型
     * @param i18n 待保存的完整动态翻译
     * @return 原始不可变翻译对象
     */
    @CheckReturnValue
    public @NotNull LocalizedFields validate(
            @NotNull ThingModel model,
            @NotNull LocalizedFields i18n) {
        Map<String, String> required = ThingModelPresentation.requiredTextPaths(
                Objects.requireNonNull(model, "物模型不能为空"));
        LocalizedFields checked = Objects.requireNonNull(i18n, "物模型多语言字段不能为空");
        if (!checked.locales().contains(locales.defaultLocale())) {
            throw new IllegalArgumentException(
                    "物模型翻译缺少默认 Locale: " + locales.defaultLocale().toLanguageTag());
        }
        Set<String> defaultKeys = checked.fields(locales.defaultLocale()).keySet();
        if (!defaultKeys.equals(required.keySet())) {
            Set<String> missing = new LinkedHashSet<>(required.keySet());
            missing.removeAll(defaultKeys);
            Set<String> unknown = new LinkedHashSet<>(defaultKeys);
            unknown.removeAll(required.keySet());
            throw new IllegalArgumentException(
                    "物模型默认翻译路径不完整: missing=" + missing + " unknown=" + unknown);
        }
        checked.locales().forEach(locale -> checked.fields(locale).forEach((path, text) -> {
            if (!required.containsKey(path)) {
                throw new IllegalArgumentException("物模型翻译包含未知路径: " + path);
            }
            if (text.length() > MAXIMUM_TEXT_LENGTH) {
                throw new IllegalArgumentException(
                        "物模型翻译超过最大长度: " + locale.toLanguageTag() + path);
            }
        }));
        return checked;
    }
}
