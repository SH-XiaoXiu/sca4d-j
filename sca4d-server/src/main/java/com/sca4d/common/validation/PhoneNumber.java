package com.sca4d.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 手机号格式校验注解。
 *
 * <p>该注解只校验中国大陆手机号的基础格式，不校验号段运营状态。
 */
@Documented
@Constraint(validatedBy = PhoneNumberValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface PhoneNumber {

    /**
     * 校验失败消息。
     *
     * @return 默认错误消息
     */
    String message() default "{validation.phone-number.invalid}";

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
