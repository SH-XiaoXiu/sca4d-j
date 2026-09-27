package com.sca4d.core.api.utils;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Objects;

/** 提供类型和方法之间的注解覆盖查找。 */
public class AnnotationUtils {

    private AnnotationUtils() {
    }

    /**
     * 优先返回方法注解，方法未声明时返回类型注解。
     *
     * @param declaringType 声明方法的类型
     * @param method 被调用方法
     * @param annotationType 注解类型
     * @param <A> 注解类型
     * @return 找到的注解，未声明时返回 {@code null}
     */
    @Contract(pure = true)
    public static <A extends Annotation> @Nullable A find(
            Class<?> declaringType,
            Method method,
            Class<A> annotationType) {
        A methodAnnotation = Objects.requireNonNull(method, "业务方法不能为空")
                .getAnnotation(Objects.requireNonNull(annotationType, "注解类型不能为空"));
        return methodAnnotation != null
                ? methodAnnotation
                : Objects.requireNonNull(declaringType, "业务类型不能为空")
                        .getAnnotation(annotationType);
    }
}
