package com.sca4d.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 展示名称格式校验注解。
 *
 * <p>该注解限制名称类文本的控制字符和最大长度，不要求字段必须存在。
 */
@Documented
@Constraint(validatedBy = DisplayNameValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface DisplayName {

    /**
     * 校验失败消息。
     *
     * @return 默认错误消息
     */
    String message() default "{validation.display-name.invalid}";

    /**
     * 最大长度。
     *
     * @return 最大字符数
     */
    int max() default 128;

    /**
     * 校验分组。
     *
     * @return 校验分组
     */
    Class<?>[] groups() default {};

    /**
     * 校验负载。
     *
     * @return 校验负载
     */
    Class<? extends Payload>[] payload() default {};
}
