package com.sca4d.core.spring.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 关闭所在 HTTP 输入及其内部值按 Java 数据类型推导的默认校验，不影响显式 Bean Validation 约束。 */
@Documented
@Target({
        ElementType.TYPE,
        ElementType.METHOD,
        ElementType.PARAMETER,
        ElementType.FIELD,
        ElementType.TYPE_USE
})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoDefaultValidation {
}
