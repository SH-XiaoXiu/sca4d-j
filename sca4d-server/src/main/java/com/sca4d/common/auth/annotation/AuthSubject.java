package com.sca4d.common.auth.annotation;

import io.swagger.v3.oas.annotations.Parameter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 注入当前登录身份。
 *
 * <p>Controller 参数类型为 {@code Subject} 时注入身份；参数类型为 {@code Long} 时注入 subjectId。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Parameter(hidden = true)
public @interface AuthSubject {

    /**
     * 是否允许当前身份为空。
     *
     * @return true 表示未登录时注入 null；false 表示未登录时抛出异常
     */
    boolean nullable() default false;
}
