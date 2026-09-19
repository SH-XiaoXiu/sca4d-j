package com.sca4d.core.spring.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 校验备注、描述等普通文本的长度和控制字符，不检查业务敏感词。 */
@Documented
@Constraint(validatedBy = SafeTextValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeText {

    /**
     * 指定约束失败时交给 MessageSource 解析的文本。
     *
     * @return 校验失败消息模板
     */
    String message() default "{validation.safe-text.invalid}";

    /**
     * 限制非空白普通文本包含的字符数。
     *
     * @return 最大字符数，默认为 255
     */
    int max() default 255;

    /**
     * 指定哪些 Bean Validation 分组执行此约束。
     *
     * @return 触发此约束的校验分组
     */
    Class<?>[] groups() default {};

    /**
     * 指定约束失败结果附带的元数据类型。
     *
     * @return 供校验结果消费者使用的负载类型
     */
    Class<? extends Payload>[] payload() default {};
}
