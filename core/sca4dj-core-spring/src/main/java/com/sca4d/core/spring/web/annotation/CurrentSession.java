package com.sca4d.core.spring.web.annotation;

import io.swagger.v3.oas.annotations.Parameter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 将当前已认证 {@link com.sca4d.core.api.authentication.Session} 注入 Controller 参数。 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Parameter(hidden = true)
public @interface CurrentSession {

    /**
     * 判断未登录时是否允许注入空值。
     *
     * @return 允许注入空值时返回 {@code true}
     */
    boolean nullable() default false;
}
