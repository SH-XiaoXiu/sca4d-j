package com.sca4d.core.spring.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 容纳同一对象上声明的多组 TimeRange 约束。 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TimeRanges {

    /** @return 对象上声明的时间范围约束 */
    TimeRange[] value();
}
