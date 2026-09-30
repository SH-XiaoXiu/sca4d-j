package com.sca4d.iot.api.model;

import com.sca4d.core.api.i18n.LocalizableText;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.api.i18n.MessageText;
import com.sca4d.core.api.i18n.TranslatedText;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 把不可变物模型技术定义与按稳定路径索引的展示文本组合成读取结果。 */
public class ThingModelPresentation {

    private final @Nullable LocalizedFields i18n;

    private final @NotNull Map<String, LocalizableText> texts;

    private final @NotNull ThingModel model;

    /** 创建数据库动态翻译对应的物模型展示结果。 */
    private ThingModelPresentation(
            @NotNull ThingModel model,
            @NotNull Map<String, ? extends LocalizableText> texts,
            @Nullable LocalizedFields i18n) {
        this.model = Objects.requireNonNull(model, "物模型不能为空");
        this.texts = Collections.unmodifiableMap(new LinkedHashMap<>(texts));
        this.i18n = i18n;
        if (!this.texts.keySet().equals(requiredTextPaths(model).keySet())) {
            throw new IllegalArgumentException("物模型展示文本必须覆盖全部且仅覆盖已定义路径");
        }
    }

    /**
     * 从数据库保存的完整动态翻译创建展示结果。
     *
     * @param model 纯技术物模型
     * @param i18n 按 Locale 和展示路径保存的翻译
     * @return 可按请求 Locale 本地化的展示结果
     */
    @CheckReturnValue
    public static @NotNull ThingModelPresentation translated(
            @NotNull ThingModel model,
            @NotNull LocalizedFields i18n) {
        ThingModel checkedModel = Objects.requireNonNull(model, "物模型不能为空");
        LocalizedFields checkedI18n = Objects.requireNonNull(i18n, "物模型多语言字段不能为空");
        Map<String, LocalizableText> texts = new LinkedHashMap<>();
        requiredTextPaths(checkedModel).forEach((path, fallback) -> {
            Map<Locale, String> translations = new LinkedHashMap<>();
            checkedI18n.locales()
                    .forEach(
                            locale -> checkedI18n
                                    .find(locale, path)
                                    .ifPresent(value -> translations.put(locale, value)));
            texts.put(path, TranslatedText.of(translations, fallback));
        });
        return new ThingModelPresentation(checkedModel, texts, checkedI18n);
    }

    /**
     * 从代码消息键创建静态物模型展示结果。
     *
     * @param model 纯技术物模型
     * @param texts 每个展示路径对应的静态消息
     * @return 不携带动态 `LocalizedFields` 的展示结果
     */
    @CheckReturnValue
    public static @NotNull ThingModelPresentation messages(
            @NotNull ThingModel model,
            @NotNull Map<String, MessageText> texts) {
        return new ThingModelPresentation(
                Objects.requireNonNull(model, "物模型不能为空"),
                Objects.requireNonNull(texts, "物模型静态消息不能为空"),
                null);
    }

    /** @return 仅使用稳定 code 作为展示回退的技术物模型结果 */
    @CheckReturnValue
    public static @NotNull ThingModelPresentation fallback(@NotNull ThingModel model) {
        ThingModel checked = Objects.requireNonNull(model, "物模型不能为空");
        Map<String, LocalizableText> texts = new LinkedHashMap<>();
        requiredTextPaths(checked).forEach((path, fallback) -> texts.put(path, TranslatedText.of(Map.of(), fallback)));
        return new ThingModelPresentation(checked, texts, null);
    }

    /** @return 纯技术物模型 */
    public @NotNull ThingModel model() {
        return model;
    }

    /** @param path 展示字段路径 @return 必须存在的可本地化文本 */
    public @NotNull LocalizableText textAt(@NotNull String path) {
        LocalizableText text = texts.get(Objects.requireNonNull(path, "物模型展示路径不能为空"));
        if (text == null) {
            throw new IllegalArgumentException("物模型展示路径不存在: " + path);
        }
        return text;
    }

    /** @return 数据库来源的完整动态翻译；代码定义没有该数据 */
    public @NotNull Optional<LocalizedFields> i18n() {
        return Optional.ofNullable(i18n);
    }

    /**
     * 返回物模型要求的全部展示路径和缺少翻译时使用的稳定 code。
     *
     * @param model 待检查的纯技术物模型
     * @return 保持模型定义顺序的不可修改路径映射
     */
    @CheckReturnValue
    public static @NotNull @Unmodifiable Map<String, String> requiredTextPaths(
            @NotNull ThingModel model) {
        ThingModel checked = Objects.requireNonNull(model, "物模型不能为空");
        Map<String, String> paths = new LinkedHashMap<>();
        paths.put("/displayName", checked.productCode());
        checked.properties().forEach((code, property) -> {
            String base = "/properties/" + escape(code);
            paths.put(base + "/displayName", code);
            valueType(paths, base + "/valueType", property.valueType());
        });
        checked.events().forEach((code, event) -> {
            String base = "/events/" + escape(code);
            paths.put(base + "/displayName", code);
            event.outputs().forEach((fieldCode, type) -> {
                String field = base + "/outputs/" + escape(fieldCode);
                paths.put(field + "/displayName", fieldCode);
                valueType(paths, field + "/valueType", type);
            });
        });
        checked.commands().forEach((code, command) -> {
            String base = "/commands/" + escape(code);
            paths.put(base + "/displayName", code);
            command.inputs().forEach((fieldCode, type) -> {
                String field = base + "/inputs/" + escape(fieldCode);
                paths.put(field + "/displayName", fieldCode);
                valueType(paths, field + "/valueType", type);
            });
        });
        return Collections.unmodifiableMap(paths);
    }

    /** 收集递归值类型中的 enum 标签和 object 字段名称。 */
    private static void valueType(Map<String, String> paths, String base, ThingValueType type) {
        type.enumValues().forEach(code -> paths.put(base + "/enumValues/" + escape(code) + "/displayName", code));
        type.requiredFields().forEach((code, fieldType) -> {
            String field = base + "/requiredFields/" + escape(code);
            paths.put(field + "/displayName", code);
            valueType(paths, field + "/valueType", fieldType);
        });
        type.optionalFields().forEach((code, fieldType) -> {
            String field = base + "/optionalFields/" + escape(code);
            paths.put(field + "/displayName", code);
            valueType(paths, field + "/valueType", fieldType);
        });
        type.elementType().ifPresent(element -> valueType(paths, base + "/elementType", element));
    }

    /** 按 RFC 6901 转义路径片段。 */
    private static @NotNull String escape(@NotNull String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }
}
