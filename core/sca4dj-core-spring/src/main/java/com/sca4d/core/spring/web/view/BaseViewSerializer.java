package com.sca4d.core.spring.web.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import com.sca4d.core.api.data.DataAssertionException;
import com.sca4d.core.api.i18n.LocalizableText;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.api.i18n.TextLocalizer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;

import java.io.IOException;
import java.io.Serial;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 在 Jackson 默认 Bean 序列化后应用本地化和 ViewJson 定制。 */
public final class BaseViewSerializer extends StdSerializer<BaseView> {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(BaseViewSerializer.class);

    /** Jackson 已为具体 View 类型创建的默认 Bean 序列化器。 */
    private final JsonSerializer<Object> delegate;

    /** 按当前请求 Locale 解析字段文本的入口。 */
    private final transient TextLocalizer localizer;

    /** 已检查目标字段与来源方法的本地化操作。 */
    @SuppressFBWarnings(value = "SE_BAD_FIELD", justification = "Jackson serializer instances are not persisted through Java serialization")
    private final List<Localization> localizations;

    /** 必须在最终 JSON 中保留并在缺失时写为 null 的字段名。 */
    private final Set<String> alwaysIncludedFields;

    /**
     * 创建特定 View 类型的最终 JSON 序列化器。
     *
     * @param viewType BaseView 具体类型
     * @param delegate Jackson 已创建的默认 Bean 序列化器
     * @param localizer 应用文本本地化入口
     * @param propertyNames Java 内部属性名到最终 JSON 名称的映射
     */
    public BaseViewSerializer(
            @NotNull Class<?> viewType,
            @NotNull JsonSerializer<Object> delegate,
            @NotNull TextLocalizer localizer,
            @NotNull Map<String, String> propertyNames) {
        super(BaseView.class);
        this.delegate = Objects.requireNonNull(delegate, "默认序列化器不能为空");
        this.localizer = Objects.requireNonNull(localizer, "TextLocalizer 不能为空");
        localizations = localizations(viewType, propertyNames);
        alwaysIncludedFields = alwaysIncludedFields(viewType, propertyNames);
    }

    /**
     * 缓冲默认 Bean JSON，依次执行全部定制，成功后只写出一次最终对象。
     *
     * @param value 当前 View
     * @param generator HTTP Jackson generator
     * @param provider 当前序列化上下文
     */
    @Override
    public void serialize(
            @NotNull BaseView value,
            @NotNull JsonGenerator generator,
            @NotNull SerializerProvider provider)
            throws IOException {
        // 先让 Jackson 默认序列化器生成完整对象，后续定制只修改这份缓冲结果。
        TokenBuffer buffer = new TokenBuffer(generator.getCodec(), false);
        delegate.serialize(value, buffer, provider);
        JsonNode node = generator.getCodec().readTree(buffer.asParser());
        if (!(node instanceof ObjectNode root)) {
            throw new DataAssertionException(null, "BaseView 默认序列化结果必须是 JSON 对象", null);
        }
        if (!(generator.getCodec() instanceof ObjectMapper objectMapper)) {
            throw new IllegalStateException("BaseView 序列化要求 generator codec 为 ObjectMapper");
        }

        // 恢复显式要求保留的 null 字段，避免包装默认序列化器后改变响应契约。
        alwaysIncludedFields.forEach(field -> {
            if (!root.has(field)) {
                root.putNull(field);
            }
        });

        // 按当前请求 Locale 写入静态或动态文本，再交给具体 View 完成最终字段定制。
        ViewJson json = new ViewJson(objectMapper, root);
        Locale requestLocale = Objects.requireNonNull(LocaleContextHolder.getLocale(), "当前请求 Locale 不能为空");
        for (Localization localization : localizations) {
            String text;
            String key = localization.key();
            if (key != null) {
                LocalizedFields fields = value.localizedFields();
                if (fields == null) {
                    throw new DataAssertionException(
                            localization.target(),
                            "动态 @Localize 要求 View 提供 LocalizedFields",
                            null);
                }
                LocalizedFields checkedFields = Objects.requireNonNull(fields, "动态本地化字段不能为空");
                try {
                    text = localizer.localize(checkedFields, key, requestLocale);
                } catch (IllegalArgumentException exception) {
                    throw new DataAssertionException(
                            localization.target(),
                            exception.getMessage(),
                            null);
                }
            } else {
                LocalizableText source = invoke(
                        Objects.requireNonNull(localization.source()),
                        value,
                        localization.target());
                text = localizer.localize(source, requestLocale);
            }
            json.put(localization.target(), text);
        }
        value.apply(json);

        // 所有定制成功后一次性写出对象，避免失败时产生不完整 JSON。
        LOG.trace(
                "BaseView 最终 JSON 已生成 | viewType={} localizedFieldCount={}",
                value.getClass().getName(),
                localizations.size());
        generator.writeTree(root);
    }

    /** 在 serializer 创建时检查并缓存当前具体 View 的全部本地化元数据。 */
    private static @NotNull List<Localization> localizations(
            @NotNull Class<?> viewType,
            @NotNull Map<String, String> propertyNames) {
        Objects.requireNonNull(propertyNames, "Jackson 属性名称映射不能为空");
        if (!BaseView.class.isAssignableFrom(viewType)) {
            throw new IllegalArgumentException("View 类型必须继承 BaseView: " + viewType.getName());
        }
        List<Localization> result = new ArrayList<>();
        Set<String> targets = new LinkedHashSet<>();
        for (Field field : fields(viewType)) {
            Localize annotation = field.getAnnotation(Localize.class);
            if (annotation == null) {
                continue;
            }
            if (field.getType() != String.class || !Modifier.isFinal(field.getModifiers())) {
                throw new IllegalStateException(
                        "@Localize 只能标注 final String 字段: "
                                + viewType.getName() + "." + field.getName());
            }
            String target = targetName(field, propertyNames);
            if (!targets.add(target)) {
                throw new IllegalStateException(
                        "BaseView 本地化目标字段重复: " + viewType.getName() + "." + target);
            }
            String source = annotation.source();
            String key = annotation.key();
            if (source.isBlank() == key.isBlank()) {
                throw new IllegalStateException(
                        "@Localize source 与 key 必须且只能填写一个: "
                                + viewType.getName() + "." + field.getName());
            }
            if (!source.isBlank()) {
                result.add(new Localization(target, sourceMethod(viewType, source), null));
            } else {
                result.add(new Localization(target, null, key));
            }
        }
        return List.copyOf(result);
    }

    /** 缓存字段级 JsonInclude(ALWAYS)，避免包装 serializer 后丢失显式 null 契约。 */
    private static @NotNull Set<String> alwaysIncludedFields(
            @NotNull Class<?> viewType,
            @NotNull Map<String, String> propertyNames) {
        Set<String> result = new LinkedHashSet<>();
        for (Field field : fields(viewType)) {
            JsonInclude inclusion = field.getAnnotation(JsonInclude.class);
            String propertyName = propertyNames.get(field.getName());
            if (inclusion != null
                    && inclusion.value() == JsonInclude.Include.ALWAYS
                    && propertyName != null) {
                result.add(propertyName);
            }
        }
        return Set.copyOf(result);
    }

    /** 收集父类到子类声明的全部字段。 */
    private static @NotNull List<Field> fields(@NotNull Class<?> viewType) {
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> type = viewType; type != null && type != Object.class; type = type.getSuperclass()) {
            hierarchy.add(type);
        }
        Collections.reverse(hierarchy);
        List<Field> fields = new ArrayList<>();
        hierarchy.forEach(type -> Collections.addAll(fields, type.getDeclaredFields()));
        return fields;
    }

    /** 使用显式 JsonProperty，否则使用 Jackson 已解析的最终属性名。 */
    private static @NotNull String targetName(
            @NotNull Field field,
            @NotNull Map<String, String> propertyNames) {
        JsonProperty property = field.getAnnotation(JsonProperty.class);
        if (property != null && !property.value().isBlank()) {
            return property.value();
        }
        return propertyNames.getOrDefault(field.getName(), field.getName());
    }

    /** 查找并校验当前类或父类中的 protected 零参数 LocalizableText source。 */
    private static @NotNull Method sourceMethod(
            @NotNull Class<?> viewType,
            @NotNull String sourceName) {
        if (sourceName.isBlank()) {
            throw new IllegalStateException("@Localize source 不能为空白: " + viewType.getName());
        }
        for (Class<?> type = viewType; type != null && type != Object.class; type = type.getSuperclass()) {
            Method method = declaredMethod(type, sourceName);
            if (method != null) {
                validateSourceMethod(viewType, sourceName, method);
                method.setAccessible(true);
                return method;
            }
        }
        throw new IllegalStateException(
                "@Localize source 方法不存在: " + viewType.getName() + "." + sourceName);
    }

    /** 查找当前类型直接声明的 source 方法，未找到时由调用方继续遍历父类。 */
    private static @Nullable Method declaredMethod(
            @NotNull Class<?> type,
            @NotNull String sourceName) {
        try {
            return type.getDeclaredMethod(sourceName);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    /** 校验 source 方法满足 BaseView 本地化契约。 */
    private static void validateSourceMethod(
            @NotNull Class<?> viewType,
            @NotNull String sourceName,
            @NotNull Method method) {
        if (!Modifier.isProtected(method.getModifiers())
                || method.getParameterCount() != 0
                || !LocalizableText.class.isAssignableFrom(method.getReturnType())) {
            throw new IllegalStateException(
                    "@Localize source 必须是 protected 零参数 LocalizableText 方法: "
                            + viewType.getName() + "." + sourceName);
        }
    }

    /** 调用本地化 source，并按约定保留业务运行时异常和 Error。 */
    private static @NotNull LocalizableText invoke(
            @NotNull Method source,
            @NotNull BaseView view,
            @NotNull String field) {
        try {
            Object result = source.invoke(view);
            if (result == null) {
                throw new DataAssertionException(field, "@Localize source 不能返回 null", null);
            }
            return (LocalizableText) result;
        } catch (IllegalAccessException | IllegalArgumentException exception) {
            throw new IllegalStateException("无法调用 @Localize source: " + source, exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("@Localize source 执行失败: " + source, cause);
        }
    }

    /** 一个经过校验的本地化目标与 source 方法。 */
    @SuppressWarnings("ClassCanBeRecord")
    private static final class Localization {

        private final String target;

        private final @Nullable Method source;

        private final @Nullable String key;

        private Localization(
                @NotNull String target,
                @Nullable Method source,
                @Nullable String key) {
            this.target = target;
            this.source = source;
            this.key = key;
        }

        /** 读取最终 JSON 中接收本地化文本的字段名。 */
        private @NotNull String target() {
            return target;
        }

        /** 读取当前 View 中返回 LocalizableText 的来源方法。 */
        private @Nullable Method source() {
            return source;
        }

        /** 读取动态国际化字段键；静态文本来源没有该值。 */
        private @Nullable String key() {
            return key;
        }
    }
}
