package com.sca4d.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * MAC 地址格式校验注解。
 *
 * <p>该注解只校验冒号分隔的 MAC 地址格式。
 */
@Documented
@Constraint(validatedBy = MacAddressValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MacAddress {

    /**
     * 校验失败消息。
     *
     * @return 默认错误消息
     */
    String message() default "{validation.mac-address.invalid}";

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
