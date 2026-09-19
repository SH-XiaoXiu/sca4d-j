package com.sca4d.core.spring.openapi;

import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.spring.config.CoreSpringProperties;
import com.sca4d.core.spring.config.HttpInputValidationProperties;
import com.sca4d.core.spring.validation.NoDefaultValidation;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.core.spring.web.annotation.ETag;
import com.sca4d.core.spring.web.annotation.IfMatch;
import com.sca4d.core.spring.web.annotation.PageOptions;
import com.sca4d.core.spring.web.list.ListParameters;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.customizers.ParameterCustomizer;
import org.springdoc.core.customizers.PropertyCustomizer;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.web.method.HandlerMethod;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 把外部 HTTP 输入的类型默认校验和显式覆盖规则写入最终 OpenAPI 请求定义。 */
public final class HttpInputOpenApiCustomizer
        implements
            ParameterCustomizer,
            PropertyCustomizer,
            OperationCustomizer,
            OpenApiCustomizer {

    static final String INTEGRAL = "x-sca4dj-http-input-integral";

    private static final String EXPLICIT_RANGE = "x-sca4dj-http-input-explicit-range";

    private static final String EXPLICIT_SIZE = "x-sca4dj-http-input-explicit-size";

    private static final String NO_DEFAULT = "x-sca4dj-http-input-no-default";

    private static final String OPERATION_NO_DEFAULT = "x-sca4dj-http-input-operation-no-default";

    private static final String POSITIVE_INTEGER_PATTERN = "^[1-9][0-9]*$";

    private static final Set<Class<? extends Annotation>> NUMERIC_RANGE_ANNOTATIONS = Set.of(
            DecimalMax.class,
            DecimalMin.class,
            Max.class,
            Min.class,
            Negative.class,
            NegativeOrZero.class,
            Positive.class,
            PositiveOrZero.class);

    private static final Set<String> MARKERS = Set.of(
            INTEGRAL,
            EXPLICIT_RANGE,
            EXPLICIT_SIZE,
            NO_DEFAULT);

    private final @NotNull HttpInputValidationProperties properties;

    private final @NotNull CoreSpringProperties coreProperties;

    /**
     * 创建与运行时 HTTP 输入默认校验共用同一份配置的 OpenAPI 定制器。
     *
     * @param properties 默认校验开关和大小限制
     * @param coreProperties 分页默认值和列表排序字段数量限制
     */
    public HttpInputOpenApiCustomizer(
            @NotNull HttpInputValidationProperties properties,
            @NotNull CoreSpringProperties coreProperties) {
        this.properties = Objects.requireNonNull(properties, "HTTP 输入校验配置不能为空");
        this.coreProperties = Objects.requireNonNull(coreProperties, "Core Spring 配置不能为空");
    }

    /**
     * 给 Springdoc 已识别的 Controller 参数添加类型和显式覆盖标记。
     *
     * @param parameterModel Springdoc 生成的参数；无法生成参数模型时为空
     * @param methodParameter Controller 方法参数
     * @return 带有标记的参数定义；输入参数模型为空时仍返回空
     */
    @Override
    public @Nullable Parameter customize(
            @Nullable Parameter parameterModel,
            @NotNull MethodParameter methodParameter) {
        if (parameterModel == null) {
            return null;
        }
        mark(
                parameterModel.getSchema(),
                methodParameter.getGenericParameterType(),
                methodParameter.getParameter().getAnnotations());
        return parameterModel;
    }

    /** @return 带有当前请求字段类型和显式覆盖标记的 Schema */
    @Override
    @SuppressWarnings("rawtypes") // springdoc PropertyCustomizer 的方法签名使用原始 Schema。
    public @NotNull Schema customize(@NotNull Schema property, @NotNull AnnotatedType type) {
        mark(property, type.getType(), type.getCtxAnnotations());
        return property;
    }

    /** @return 带有方法级关闭标记和请求体根类型标记的接口定义 */
    @Override
    public @NotNull Operation customize(
            @NotNull Operation operation,
            @NotNull HandlerMethod handlerMethod) {
        if (hasNoDefaultValidation(handlerMethod.getMethod())
                || hasNoDefaultValidation(handlerMethod.getBeanType())) {
            operation.addExtension(OPERATION_NO_DEFAULT, true);
        }
        if (handlerMethod.hasMethodAnnotation(IfMatch.class)) {
            operation.addParametersItem(
                    new Parameter()
                            .name(HttpHeaders.IF_MATCH)
                            .in("header")
                            .required(true)
                            .description("资源强 ETag")
                            .example("\"3\"")
                            .schema(new StringSchema()));
        }
        if (hasResponseETag(handlerMethod.getMethod())) {
            addResponseETag(operation);
        }
        java.lang.reflect.Parameter[] parameters = handlerMethod.getMethod().getParameters();
        for (java.lang.reflect.Parameter parameter : parameters) {
            if (PageRequest.class.equals(parameter.getType())) {
                PageOptions options = parameter.getAnnotation(PageOptions.class);
                int maxSize = options == null
                        ? coreProperties.getPageMaxSize()
                        : Math.min(coreProperties.getPageMaxSize(), options.maxSize());
                addPageParameters(operation, maxSize);
            } else if (ListParameters.class.equals(parameter.getType())) {
                addPageParameters(operation, coreProperties.getPageMaxSize());
                addSortParameter(operation);
            }
            if (!parameter.isAnnotationPresent(org.springframework.web.bind.annotation.RequestBody.class)) {
                continue;
            }
            RequestBody requestBody = operation.getRequestBody();
            if (requestBody != null) {
                mark(requestBody.getContent(), parameter.getParameterizedType(), parameter.getAnnotations());
            }
        }
        return operation;
    }

    /** 给成功响应声明资源强 ETag 响应头。 */
    private static void addResponseETag(@NotNull Operation operation) {
        if (operation.getResponses() == null) {
            return;
        }
        operation.getResponses().forEach((status, response) -> {
            if (status.length() == 3 && status.charAt(0) == '2') {
                response.addHeaderObject(
                        HttpHeaders.ETAG,
                        new Header().description("资源强 ETag").schema(new StringSchema()));
            }
        });
    }

    /** @return Controller 返回的 {@code ApiResponse.data} 类型是否声明了 {@link ETag} */
    private static boolean hasResponseETag(@NotNull Method method) {
        Type returnType = Objects.requireNonNull(method, "Controller 方法不能为空").getGenericReturnType();
        if (!(returnType instanceof ParameterizedType responseType)
                || !ApiResponse.class.equals(responseType.getRawType())) {
            return false;
        }
        Type dataType = responseType.getActualTypeArguments()[0];
        if (!(dataType instanceof Class<?> dataClass)) {
            return false;
        }
        return Arrays.stream(dataClass.getMethods())
                .anyMatch(candidate -> candidate.isAnnotationPresent(ETag.class));
    }

    /** 向分页接口补充 Springdoc 无法从参数解析器推断的 page 和 size 参数。 */
    private void addPageParameters(@NotNull Operation operation, int maxSize) {
        if (!hasParameter(operation, "page")) {
            IntegerSchema page = new IntegerSchema();
            page.setMinimum(BigDecimal.ONE);
            page.setDefault(1);
            operation.addParametersItem(
                    new Parameter()
                            .name("page")
                            .in("query")
                            .description("从 1 开始的页码")
                            .schema(page));
        }
        if (!hasParameter(operation, "size")) {
            IntegerSchema size = new IntegerSchema();
            size.setMinimum(BigDecimal.ONE);
            size.setMaximum(BigDecimal.valueOf(maxSize));
            size.setDefault(coreProperties.getPageDefaultSize());
            operation.addParametersItem(
                    new Parameter()
                            .name("size")
                            .in("query")
                            .description("当前页最多返回的记录数量")
                            .schema(size));
        }
    }

    /** 向动态排序接口补充保持出现顺序的重复 sort 参数。 */
    private void addSortParameter(@NotNull Operation operation) {
        if (hasParameter(operation, "sort")) {
            return;
        }
        StringSchema item = new StringSchema();
        item.setPattern("^[A-Z][A-Z0-9_]*,(ASC|DESC)$");
        ArraySchema sort = new ArraySchema();
        sort.setItems(item);
        sort.setMaxItems(coreProperties.getSortMaxFields());
        operation.addParametersItem(
                new Parameter()
                        .name("sort")
                        .in("query")
                        .description("按出现顺序应用的 FIELD,DIRECTION，可重复提交；允许字段由具体接口声明")
                        .style(Parameter.StyleEnum.FORM)
                        .explode(true)
                        .schema(sort));
    }

    /** @return 当前接口是否已经包含指定名称的参数 */
    private static boolean hasParameter(@NotNull Operation operation, @NotNull String name) {
        return operation.getParameters() != null
                && operation.getParameters().stream().anyMatch(parameter -> name.equals(parameter.getName()));
    }

    /**
     * 只向接口参数和请求体写入默认限制，并从最终文档删除生成期间使用的内部标记。
     *
     * @param openApi Springdoc 已生成的 OpenAPI 文档
     */
    @Override
    public void customise(@NotNull OpenAPI openApi) {
        Components components = openApi.getComponents();
        if (openApi.getPaths() != null) {
            for (PathItem path : openApi.getPaths().values()) {
                for (Operation operation : path.readOperations()) {
                    boolean disabled = hasExtension(operation.getExtensions(), OPERATION_NO_DEFAULT);
                    removeOperationMarker(operation.getExtensions());
                    if (!disabled) {
                        apply(operation.getParameters(), operation.getRequestBody(), components);
                    }
                }
            }
        }
        clearAllMarkers(openApi, components);
    }

    /** 在参数或请求体根 Schema 上记录 Java 声明提供的校验信息。 */
    private void mark(
            @Nullable Content content,
            @NotNull Type type,
            Annotation @NotNull [] annotations) {
        if (content == null) {
            return;
        }
        for (MediaType mediaType : content.values()) {
            mark(mediaType.getSchema(), type, annotations);
        }
    }

    /** 在 Schema 上记录 Java 声明提供的校验信息。 */
    private void mark(
            @Nullable Schema<?> schema,
            @NotNull Type type,
            Annotation @Nullable [] annotations) {
        if (schema == null) {
            return;
        }
        if (isIntegral(type)) {
            schema.addExtension(INTEGRAL, true);
        }
        if (hasNumericRangeAnnotation(annotations)) {
            schema.addExtension(EXPLICIT_RANGE, true);
        }
        if (hasAnnotation(annotations, Size.class)) {
            schema.addExtension(EXPLICIT_SIZE, true);
        }
        if (hasAnnotation(annotations, NoDefaultValidation.class)) {
            schema.addExtension(NO_DEFAULT, true);
        }
        if (schema.getItems() != null
                && type instanceof ParameterizedType parameterized
                && Collection.class.isAssignableFrom(rawType(type))) {
            Type[] arguments = parameterized.getActualTypeArguments();
            if (arguments.length == 1) {
                mark(schema.getItems(), arguments[0], null);
            }
        }
    }

    /** 向一个接口的全部外部输入 Schema 应用默认限制。 */
    private void apply(
            @Nullable List<Parameter> parameters,
            @Nullable RequestBody requestBody,
            @Nullable Components components) {
        Set<Schema<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        if (parameters != null) {
            for (Parameter parameter : parameters) {
                apply(parameter.getSchema(), components, visited);
                apply(parameter.getContent(), components, visited);
            }
        }
        if (requestBody != null) {
            apply(requestBody.getContent(), components, visited);
        }
    }

    /** 向 Content 内每一种媒体类型的 Schema 应用默认限制。 */
    private void apply(
            @Nullable Content content,
            @Nullable Components components,
            @NotNull Set<Schema<?>> visited) {
        if (content == null) {
            return;
        }
        for (MediaType mediaType : content.values()) {
            apply(mediaType.getSchema(), components, visited);
        }
    }

    /** 按 Schema 类型应用对应默认限制，并继续检查对象字段和数组元素。 */
    private void apply(
            @Nullable Schema<?> source,
            @Nullable Components components,
            @NotNull Set<Schema<?>> visited) {
        Schema<?> schema = resolve(source, components);
        if (schema == null
                || hasExtension(source.getExtensions(), NO_DEFAULT)
                || hasExtension(schema.getExtensions(), NO_DEFAULT)
                || !visited.add(schema)) {
            return;
        }
        try {
            boolean integral = hasExtension(source.getExtensions(), INTEGRAL)
                    || hasExtension(schema.getExtensions(), INTEGRAL);
            boolean explicitRange = hasExtension(source.getExtensions(), EXPLICIT_RANGE)
                    || hasExtension(schema.getExtensions(), EXPLICIT_RANGE);
            boolean explicitSize = hasExtension(source.getExtensions(), EXPLICIT_SIZE)
                    || hasExtension(schema.getExtensions(), EXPLICIT_SIZE);
            if (integral && properties.isPositiveIntegerEnabled() && !explicitRange) {
                if (hasType(schema, "string")) {
                    if (schema.getPattern() == null) {
                        schema.setPattern(POSITIVE_INTEGER_PATTERN);
                    }
                } else {
                    schema.setMinimum(BigDecimal.ONE);
                    schema.setExclusiveMinimum(null);
                }
            } else if (hasType(schema, "string")
                    && properties.isStringSizeEnabled()
                    && !explicitSize) {
                schema.setMaxLength(properties.getMaxStringLength());
            }

            if (hasType(schema, "array")) {
                if (properties.isContainerSizeEnabled() && !explicitSize) {
                    schema.setMaxItems(properties.getMaxContainerSize());
                }
                apply(schema.getItems(), components, visited);
                return;
            }
            if (hasType(schema, "object") && schema.getAdditionalProperties() != null) {
                if (properties.isContainerSizeEnabled() && !explicitSize) {
                    schema.setMaxProperties(properties.getMaxContainerSize());
                }
                return;
            }
            if (schema.getProperties() != null) {
                for (Schema<?> property : schema.getProperties().values()) {
                    apply(property, components, visited);
                }
            }
            applyCombinedSchemas(schema.getAllOf(), components, visited);
            applyCombinedSchemas(schema.getAnyOf(), components, visited);
            applyCombinedSchemas(schema.getOneOf(), components, visited);
        } finally {
            visited.remove(schema);
        }
    }

    /** 向组合 Schema 的每个分支应用默认限制。 */
    private void applyCombinedSchemas(
            @Nullable List<?> schemas,
            @Nullable Components components,
            @NotNull Set<Schema<?>> visited) {
        if (schemas != null) {
            for (Object candidate : schemas) {
                if (candidate instanceof Schema<?> schema) {
                    apply(schema, components, visited);
                }
            }
        }
    }

    /** @return 解析本地 components 引用后的 Schema */
    private @Nullable Schema<?> resolve(
            @Nullable Schema<?> schema,
            @Nullable Components components) {
        if (schema == null || schema.get$ref() == null || components == null || components.getSchemas() == null) {
            return schema;
        }
        String prefix = "#/components/schemas/";
        return schema.get$ref().startsWith(prefix)
                ? components.getSchemas().get(schema.get$ref().substring(prefix.length()))
                : schema;
    }

    /** @return Schema 是否声明了指定的 OpenAPI 类型 */
    private boolean hasType(@NotNull Schema<?> schema, @NotNull String type) {
        return type.equals(schema.getType())
                || schema.getTypes() != null && schema.getTypes().contains(type);
    }

    /** 清理组件和接口内所有可到达 Schema 上的内部标记。 */
    private void clearAllMarkers(@NotNull OpenAPI openApi, @Nullable Components components) {
        Set<Schema<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        if (components != null && components.getSchemas() != null) {
            for (Schema<?> schema : components.getSchemas().values()) {
                clearMarkers(schema, components, visited);
            }
        }
        if (openApi.getPaths() == null) {
            return;
        }
        for (PathItem path : openApi.getPaths().values()) {
            for (Operation operation : path.readOperations()) {
                clearMarkers(operation.getParameters(), operation.getRequestBody(), components, visited);
                if (operation.getResponses() != null) {
                    operation.getResponses()
                            .values()
                            .forEach(
                                    response -> clearMarkers(
                                            response.getContent(),
                                            components,
                                            visited));
                }
            }
        }
    }

    /** 清理接口参数和请求体中的内部 Schema 标记。 */
    private void clearMarkers(
            List<Parameter> parameters,
            @Nullable RequestBody requestBody,
            @Nullable Components components,
            @NotNull Set<Schema<?>> visited) {
        if (parameters != null) {
            for (Parameter parameter : parameters) {
                clearMarkers(parameter.getSchema(), components, visited);
                clearMarkers(parameter.getContent(), components, visited);
            }
        }
        if (requestBody != null) {
            clearMarkers(requestBody.getContent(), components, visited);
        }
    }

    /** 清理 Content 内每一种媒体类型的 Schema 标记。 */
    private void clearMarkers(
            @Nullable Content content,
            @Nullable Components components,
            @NotNull Set<Schema<?>> visited) {
        if (content != null) {
            for (MediaType mediaType : content.values()) {
                clearMarkers(mediaType.getSchema(), components, visited);
            }
        }
    }

    /** 清理当前 Schema 和所有子 Schema 上的内部标记。 */
    private void clearMarkers(
            @Nullable Schema<?> source,
            @Nullable Components components,
            @NotNull Set<Schema<?>> visited) {
        if (source == null || !visited.add(source)) {
            return;
        }
        removeMarkers(source);
        Schema<?> resolved = resolve(source, components);
        if (resolved != source) {
            clearMarkers(resolved, components, visited);
        }
        if (source.getProperties() != null) {
            for (Schema<?> property : source.getProperties().values()) {
                clearMarkers(property, components, visited);
            }
        }
        clearMarkers(source.getItems(), components, visited);
        if (source.getAdditionalProperties() instanceof Schema<?> additional) {
            clearMarkers(additional, components, visited);
        }
        clearCombinedMarkers(source.getAllOf(), components, visited);
        clearCombinedMarkers(source.getAnyOf(), components, visited);
        clearCombinedMarkers(source.getOneOf(), components, visited);
    }

    /** 清理组合 Schema 的每个分支。 */
    private void clearCombinedMarkers(
            @Nullable List<?> schemas,
            @Nullable Components components,
            @NotNull Set<Schema<?>> visited) {
        if (schemas != null) {
            for (Object candidate : schemas) {
                if (candidate instanceof Schema<?> schema) {
                    clearMarkers(schema, components, visited);
                }
            }
        }
    }

    /** 删除一个 Schema 上的全部内部标记。 */
    private void removeMarkers(@NotNull Schema<?> schema) {
        Map<String, Object> extensions = schema.getExtensions();
        if (extensions != null) {
            MARKERS.forEach(extensions::remove);
            if (extensions.isEmpty()) {
                schema.setExtensions(null);
            }
        }
    }

    /** @return Java 声明类型是否属于默认正数规则支持的整数类型 */
    private boolean isIntegral(@NotNull Type type) {
        Class<?> rawType = rawType(type);
        return rawType == byte.class
                || rawType == Byte.class
                || rawType == short.class
                || rawType == Short.class
                || rawType == int.class
                || rawType == Integer.class
                || rawType == long.class
                || rawType == Long.class
                || rawType == BigInteger.class;
    }

    /** @return Type 中可以直接判断的原始 Class，无法判断时返回 Object */
    private @NotNull Class<?> rawType(@NotNull Type type) {
        if (type instanceof Class<?> rawType) {
            return rawType;
        }
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> rawType) {
            return rawType;
        }
        return Object.class;
    }

    /** @return 声明或其组合注解中是否包含任一目标注解 */
    private boolean hasNumericRangeAnnotation(Annotation @Nullable [] annotations) {
        return NUMERIC_RANGE_ANNOTATIONS.stream().anyMatch(type -> hasAnnotation(annotations, type));
    }

    /** @return 声明或其组合注解中是否包含目标注解 */
    private boolean hasAnnotation(
            Annotation @Nullable [] annotations,
            @NotNull Class<? extends Annotation> annotationType) {
        if (annotations == null) {
            return false;
        }
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().equals(annotationType)
                    || annotation.annotationType().isAnnotationPresent(annotationType)) {
                return true;
            }
        }
        return false;
    }

    /** @return 声明或其组合注解中是否包含目标注解 */
    private boolean hasNoDefaultValidation(@NotNull AnnotatedElement element) {
        return hasAnnotation(element.getAnnotations(), NoDefaultValidation.class);
    }

    /** @return extensions 是否包含值为 true 的指定标记 */
    private boolean hasExtension(
            @Nullable Map<String, Object> extensions,
            @NotNull String name) {
        return extensions != null && Boolean.TRUE.equals(extensions.get(name));
    }

    /** 删除一个 extensions 标记，并在其为空时交由模型保留空 Map。 */
    private void removeOperationMarker(@Nullable Map<String, Object> extensions) {
        if (extensions != null) {
            extensions.remove(OPERATION_NO_DEFAULT);
        }
    }
}
