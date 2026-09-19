package com.sca4d.core.spring.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 校验数字 ID、固定业务编码或命名空间编码的格式，不表达必填含义。 */
@Documented
@Constraint(validatedBy = BizIdValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface BizId {

    /**
     * 指定约束失败时交给 MessageSource 解析的文本。
     *
     * @return 校验失败消息模板
     */
    String message() default "{validation.biz-id.invalid}";

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

    /**
     * 选择数字、业务编码、命名空间编码或自定义正则格式。
     *
     * @return 业务标识格式，默认为 CODE
     */
    BizIdMode mode() default BizIdMode.CODE;

    /**
     * 限制字符串形式包含的最少字符数。
     *
     * @return 字符串形式允许的最小长度，默认为 1
     */
    int min() default 1;

    /**
     * 限制字符串形式包含的最多字符数。
     *
     * @return 字符串形式允许的最大长度，默认为 64
     */
    int max() default 64;

    /**
     * 指定 CUSTOM 模式检查完整字符串时使用的表达式。
     *
     * @return CUSTOM 模式使用的正则表达式，其他模式忽略
     */
    String regexp() default "";
}
