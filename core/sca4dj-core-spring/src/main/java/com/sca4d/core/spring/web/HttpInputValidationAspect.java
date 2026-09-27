package com.sca4d.core.spring.web;

import com.sca4d.core.api.i18n.MessageText;
import com.sca4d.core.api.i18n.TextLocalizer;
import com.sca4d.core.spring.config.HttpInputValidationProperties;
import com.sca4d.core.spring.validation.NoDefaultValidation;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.MatrixVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 在 Controller 执行前校验外部 HTTP 输入的显式约束和按 Java 数据类型配置的默认规则。 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class HttpInputValidationAspect {

    private static final Set<Class<? extends Annotation>> NUMERIC_RANGE_ANNOTATIONS = Set.of(
            DecimalMax.class,
            DecimalMin.class,
            Max.class,
            Min.class,
            Negative.class,
            NegativeOrZero.class,
            Positive.class,
            PositiveOrZero.class);

    private final @NotNull Validator validator;

    private final @NotNull TextLocalizer localizer;

    private final @NotNull HttpInputValidationProperties properties;

    /**
     * 创建使用共享 Bean Validation 和 Core 国际化目录的 HTTP 输入校验器。
     *
     * @param validator 处理请求体显式约束的 Bean Validation 入口
     * @param localizer 生成默认校验失败消息的本地化入口
     * @param properties 默认校验开关和大小限制
     */
    public HttpInputValidationAspect(
            @NotNull Validator validator,
            @NotNull TextLocalizer localizer,
            @NotNull HttpInputValidationProperties properties) {
        this.validator = Objects.requireNonNull(validator, "Bean Validation 入口不能为空");
        this.localizer = Objects.requireNonNull(localizer, "TextLocalizer 不能为空");
        this.properties = Objects.requireNonNull(properties, "HTTP 输入校验配置不能为空");
    }

    /**
     * 校验 Controller 的外部绑定参数后执行原方法。
     *
     * @param joinPoint 已完成 Spring MVC 参数绑定的 Controller 调用
     * @return Controller 方法结果
     * @throws Throwable 显式约束、默认规则或 Controller 方法执行失败时抛出
     */
    @Around("@within(org.springframework.web.bind.annotation.RestController)"
            + " || @within(org.springframework.stereotype.Controller)")
    public @Nullable Object validate(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] arguments = joinPoint.getArgs();

        validateRequestBodies(parameters, arguments);

        if (!hasAnnotation(method, NoDefaultValidation.class)
                && !hasAnnotation(method.getDeclaringClass(), NoDefaultValidation.class)) {
            List<FieldViolation> violations = new ArrayList<>();
            Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            for (int index = 0; index < parameters.length; index++) {
                Parameter parameter = parameters[index];
                if (isExternalInput(parameter)) {
                    validateValue(
                            arguments[index],
                            parameter.getAnnotatedType(),
                            parameter,
                            parameter.isAnnotationPresent(RequestBody.class) ? "" : inputName(parameter),
                            violations,
                            visited);
                }
            }
            if (!violations.isEmpty()) {
                throw new HttpInputValidationException(violations);
            }
        }
        return joinPoint.proceed();
    }

    /**
     * 执行请求体对象声明的 Bean Validation 约束。
     *
     * @param parameters Controller 方法参数
     * @param arguments 已完成绑定的参数值
     * @throws ConstraintViolationException 任一请求体未满足显式约束时抛出
     */
    private void validateRequestBodies(Parameter[] parameters, Object[] arguments) {
        if (!properties.isRequestBodyValidationEnabled()) {
            return;
        }
        Set<ConstraintViolation<?>> violations = new LinkedHashSet<>();
        for (int index = 0; index < parameters.length; index++) {
            if (parameters[index].isAnnotationPresent(RequestBody.class) && arguments[index] != null) {
                violations.addAll(validator.validate(arguments[index]));
            }
        }
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    /**
     * 校验一个外部输入值，并递归读取其中的容器元素或普通对象字段。
     *
     * @param value 当前值
     * @param annotatedType 当前声明类型及类型使用注解
     * @param declaration 当前参数或字段声明
     * @param path 客户端使用的字段路径
     * @param violations 已发现的失败结果
     * @param visited 已访问的对象身份
     */
    private void validateValue(
            @Nullable Object value,
            @NotNull AnnotatedType annotatedType,
            @NotNull AnnotatedElement declaration,
            @NotNull String path,
            @NotNull List<FieldViolation> violations,
            @NotNull Set<Object> visited) {
        if (value == null
                || hasAnnotation(declaration, NoDefaultValidation.class)
                || hasAnnotation(annotatedType, NoDefaultValidation.class)
                || hasAnnotation(value.getClass(), NoDefaultValidation.class)) {
            return;
        }

        if (isIntegral(value)) {
            if (properties.isPositiveIntegerEnabled()
                    && !hasNumericRangeConstraint(declaration, annotatedType)
                    && !isPositive(value)) {
                violations.add(new FieldViolation(path, localize("validation.positive")));
            }
            return;
        }
        switch (value) {
            case CharSequence text -> {
                if (properties.isStringSizeEnabled()
                        && lacksSizeConstraint(declaration, annotatedType)
                        && text.length() > properties.getMaxStringLength()) {
                    violations.add(
                            new FieldViolation(
                                    path,
                                    localize("validation.string-size", properties.getMaxStringLength())));
                }
                return;
            }
            case Collection<?> collection -> {
                if (!visited.add(value)) {
                    return;
                }
                validateContainerSize(collection.size(), declaration, annotatedType, path, violations);
                AnnotatedType elementType = collectionElementType(annotatedType);
                int index = 0;
                for (Object element : collection) {
                    validateValue(element, elementType, elementType, indexed(path, index), violations, visited);
                    index++;
                }
                return;
            }
            case Map<?, ?> map -> {
                validateContainerSize(map.size(), declaration, annotatedType, path, violations);
                return;
            }
            default -> {
                // 数组和普通请求对象需要继续读取运行时类型。
            }
        }
        if (value.getClass().isArray()) {
            if (value instanceof byte[] || value instanceof char[]) {
                return;
            }
            if (!visited.add(value)) {
                return;
            }
            int length = Array.getLength(value);
            validateContainerSize(length, declaration, annotatedType, path, violations);
            AnnotatedType elementType = arrayElementType(annotatedType);
            for (int index = 0; index < length; index++) {
                validateValue(
                        Array.get(value, index),
                        elementType,
                        elementType,
                        indexed(path, index),
                        violations,
                        visited);
            }
            return;
        }
        if (!shouldTraverse(value.getClass()) || !visited.add(value)) {
            return;
        }

        for (Field field : fields(value.getClass())) {
            if (!field.trySetAccessible()) {
                throw new IllegalStateException(
                        "无法读取 HTTP 请求字段 " + field.getDeclaringClass().getName() + "." + field.getName());
            }
            try {
                validateValue(
                        field.get(value),
                        field.getAnnotatedType(),
                        field,
                        child(path, field.getName()),
                        violations,
                        visited);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("无法读取 HTTP 请求字段 " + field.getName(), exception);
            }
        }
    }

    /**
     * 检查 Collection、Map 或非二进制数组的默认最大元素数。
     *
     * @param size 当前元素数
     * @param declaration 参数或字段声明
     * @param annotatedType 当前声明类型
     * @param path 字段路径
     * @param violations 已发现的失败结果
     */
    private void validateContainerSize(
            int size,
            @NotNull AnnotatedElement declaration,
            @NotNull AnnotatedType annotatedType,
            @NotNull String path,
            @NotNull List<FieldViolation> violations) {
        if (properties.isContainerSizeEnabled()
                && lacksSizeConstraint(declaration, annotatedType)
                && size > properties.getMaxContainerSize()) {
            violations.add(
                    new FieldViolation(
                            path,
                            localize("validation.container-size", properties.getMaxContainerSize())));
        }
    }

    /** @return 参数是否由 Spring MVC 从外部 HTTP 请求绑定 */
    private boolean isExternalInput(@NotNull Parameter parameter) {
        return parameter.isAnnotationPresent(PathVariable.class)
                || parameter.isAnnotationPresent(RequestParam.class)
                || parameter.isAnnotationPresent(RequestHeader.class)
                || parameter.isAnnotationPresent(CookieValue.class)
                || parameter.isAnnotationPresent(MatrixVariable.class)
                || parameter.isAnnotationPresent(RequestPart.class)
                || parameter.isAnnotationPresent(RequestBody.class)
                || parameter.isAnnotationPresent(ModelAttribute.class);
    }

    /** @return 客户端发送当前参数时使用的名称 */
    private @NotNull String inputName(@NotNull Parameter parameter) {
        PathVariable path = parameter.getAnnotation(PathVariable.class);
        if (path != null && !path.name().isBlank()) {
            return path.name();
        }
        RequestParam query = parameter.getAnnotation(RequestParam.class);
        if (query != null && !query.name().isBlank()) {
            return query.name();
        }
        RequestHeader header = parameter.getAnnotation(RequestHeader.class);
        if (header != null && !header.name().isBlank()) {
            return header.name();
        }
        CookieValue cookie = parameter.getAnnotation(CookieValue.class);
        if (cookie != null && !cookie.name().isBlank()) {
            return cookie.name();
        }
        MatrixVariable matrix = parameter.getAnnotation(MatrixVariable.class);
        if (matrix != null && !matrix.name().isBlank()) {
            return matrix.name();
        }
        RequestPart part = parameter.getAnnotation(RequestPart.class);
        if (part != null && !part.name().isBlank()) {
            return part.name();
        }
        return parameter.getName();
    }

    /** @return 当前参数或类型是否声明了显式数值范围约束 */
    private boolean hasNumericRangeConstraint(
            @NotNull AnnotatedElement declaration,
            @NotNull AnnotatedType annotatedType) {
        return hasNumericRangeAnnotation(declaration)
                || hasNumericRangeAnnotation(annotatedType);
    }

    /** @return 当前参数和类型是否都没有声明显式 {@code @Size} 约束 */
    private boolean lacksSizeConstraint(
            @NotNull AnnotatedElement declaration,
            @NotNull AnnotatedType annotatedType) {
        return !hasAnnotation(declaration, Size.class) && !hasAnnotation(annotatedType, Size.class);
    }

    /** @return 声明或组合注解中是否包含显式数值范围注解 */
    private boolean hasNumericRangeAnnotation(@NotNull AnnotatedElement element) {
        return NUMERIC_RANGE_ANNOTATIONS.stream().anyMatch(type -> hasAnnotation(element, type));
    }

    /** @return 声明或组合注解中是否包含目标注解 */
    private boolean hasAnnotation(
            @NotNull AnnotatedElement element,
            @NotNull Class<? extends Annotation> annotationType) {
        if (element.isAnnotationPresent(annotationType)) {
            return true;
        }
        for (Annotation annotation : element.getAnnotations()) {
            if (annotation.annotationType().getName().startsWith("java.lang.annotation.")) {
                continue;
            }
            if (annotation.annotationType().isAnnotationPresent(annotationType)) {
                return true;
            }
        }
        return false;
    }

    /** @return 值是否属于默认正数规则支持的整数类型 */
    private boolean isIntegral(@NotNull Object value) {
        return value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof BigInteger;
    }

    /** @return 整数值是否大于零 */
    private boolean isPositive(@NotNull Object value) {
        if (value instanceof BigInteger integer) {
            return integer.signum() > 0;
        }
        return ((Number) value).longValue() > 0;
    }

    /** @return Collection 声明的元素类型，无法读取时返回 Object 类型 */
    private @NotNull AnnotatedType collectionElementType(@NotNull AnnotatedType type) {
        if (type instanceof AnnotatedParameterizedType parameterized
                && parameterized.getAnnotatedActualTypeArguments().length == 1) {
            return parameterized.getAnnotatedActualTypeArguments()[0];
        }
        return ObjectType.INSTANCE;
    }

    /** @return 数组声明的元素类型，无法读取时返回 Object 类型 */
    private @NotNull AnnotatedType arrayElementType(@NotNull AnnotatedType type) {
        return type instanceof AnnotatedArrayType array
                ? array.getAnnotatedGenericComponentType()
                : ObjectType.INSTANCE;
    }

    /** @return 当前类型是否应该按普通请求对象继续读取字段 */
    private boolean shouldTraverse(@NotNull Class<?> type) {
        if (type.isPrimitive()
                || type.isEnum()
                || Number.class.isAssignableFrom(type)
                || Boolean.class.equals(type)
                || Character.class.equals(type)
                || Date.class.isAssignableFrom(type)
                || TemporalAccessor.class.isAssignableFrom(type)
                || UUID.class.equals(type)) {
            return false;
        }
        Package typePackage = type.getPackage();
        String packageName = typePackage == null ? "" : typePackage.getName();
        return !packageName.startsWith("java.")
                && !packageName.startsWith("jakarta.")
                && !packageName.startsWith("org.springframework.");
    }

    /** @return 当前类型及父类中需要校验的实例字段 */
    private @NotNull List<@NotNull Field> fields(@NotNull Class<?> type) {
        List<Field> result = new ArrayList<>();
        for (Class<?> current = type; current != null
                && !Object.class.equals(current); current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    result.add(field);
                }
            }
        }
        return result;
    }

    /** @return 请求体子字段路径 */
    private @NotNull String child(@NotNull String parent, @NotNull String name) {
        return parent.isBlank() || parent.startsWith("arg") ? name : parent + "." + name;
    }

    /** @return 容器元素路径 */
    private @NotNull String indexed(@NotNull String parent, int index) {
        return parent + "[" + index + "]";
    }

    /** @return 当前语言的默认校验失败文本 */
    private @NotNull String localize(@NotNull String key, Object @NotNull... arguments) {
        return localizer.localize(
                MessageText.of(key, arguments),
                LocaleContextHolder.getLocale());
    }

    /** 提供反射无法解析泛型元素时使用的无注解 Object 类型。 */
    private static class ObjectType implements AnnotatedType {

        private static final ObjectType INSTANCE = new ObjectType();

        @Override
        public Type getType() {
            return Object.class;
        }

        @Override
        public <T extends Annotation> @Nullable T getAnnotation(@NotNull Class<T> annotationClass) {
            return null;
        }

        @Override
        public Annotation @NotNull [] getAnnotations() {
            return new Annotation[0];
        }

        @Override
        public Annotation @NotNull [] getDeclaredAnnotations() {
            return new Annotation[0];
        }
    }
}
