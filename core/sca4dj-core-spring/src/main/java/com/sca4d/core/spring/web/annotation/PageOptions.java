package com.sca4d.core.spring.web.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 为一个 {@code PageRequest} Controller 参数声明比全局配置更小的每页数量上限。 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface PageOptions {

    /** @return 当前接口允许的最大每页数量 */
    int maxSize();
}
