package com.sca4d.core.spring.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 校验同一对象的两个 Instant 属性构成严格递增的时间范围。 */
@Documented
@Constraint(validatedBy = TimeRangeValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(TimeRanges.class)
public @interface TimeRange {

    /** @return 开始时间的 JavaBean 属性名 */
    String start();

    /** @return 结束时间的 JavaBean 属性名，校验失败指向此属性 */
    String end();

    /** @return 校验失败时解析的消息模板 */
    String message() default "{validation.time-range.invalid}";

    /** @return 执行此约束的 Bean Validation 分组 */
    Class<?>[] groups() default {};

    /** @return 约束失败结果附带的元数据类型 */
    Class<? extends Payload>[] payload() default {};
}
