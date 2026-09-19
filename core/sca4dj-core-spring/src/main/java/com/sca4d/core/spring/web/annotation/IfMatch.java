package com.sca4d.core.spring.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 要求 HTTP 方法提交一个强 {@code If-Match} ETag，并由 Repository 消费对应版本条件。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IfMatch {
}
