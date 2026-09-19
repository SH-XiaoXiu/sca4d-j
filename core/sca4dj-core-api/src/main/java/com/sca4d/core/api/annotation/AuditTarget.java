package com.sca4d.core.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标记方法参数中的简单审计目标 Resource 标识。 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditTarget {

    /**
     * 返回目标 Resource 类型。
     *
     * @return 非空资源类型
     */
    String resourceType();

    /**
     * 返回拼接到数字标识前的可选前缀。
     *
     * @return 资源标识前缀
     */
    String idPrefix() default "";
}
