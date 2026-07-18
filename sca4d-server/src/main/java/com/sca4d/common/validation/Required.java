package com.sca4d.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotNull;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 请求字段必填校验注解。
 *
 * <p>该注解组合 Bean Validation 的 {@link NotNull}，用于请求对象的运行时校验。
 * 方法签名的空值语义仍使用 JetBrains 注解表达。
 */
@Documented
@NotNull
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({
        ElementType.FIELD,
        ElementType.PARAMETER,
        ElementType.METHOD,
        ElementType.TYPE_USE,
        ElementType.ANNOTATION_TYPE
})
@Retention(RetentionPolicy.RUNTIME)
public @interface Required {

    /** @return 默认错误消息 */
    String message() default "{jakarta.validation.constraints.NotNull.message}";

    /** @return 校验分组 */
    Class<?>[] groups() default {};

    /** @return 校验负载 */
    Class<? extends Payload>[] payload() default {};
}
