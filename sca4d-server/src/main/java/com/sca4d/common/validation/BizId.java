package com.sca4d.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 业务标识校验注解。
 *
 * <p>{@link BizIdMode#CODE} 用于例如设备编号、订单编号等固定文本编码；
 * {@link BizIdMode#NAMESPACED_CODE} 用于点分资源、场景或类型编码；
 * {@link BizIdMode#NUM} 用于正整数 ID；{@link BizIdMode#CUSTOM} 用于业务侧明确拥有的自定义格式。
 * 该注解只校验格式，不表达必填含义；必填请组合使用 {@code @NotBlank} 或 {@code @NotNull}。
 */
@Documented
@Constraint(validatedBy = BizIdValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface BizId {

    /**
     * 校验失败消息。
     *
     * @return 默认错误消息
     */
    String message() default "{validation.biz-id.invalid}";

    /**
     * 校验分组。
     *
     * @return 校验分组
     */
    Class<?>[] groups() default {};

    /**
     * 校验负载。
     *
     * @return 校验负载
     */
    Class<? extends Payload>[] payload() default {};

    /**
     * 业务标识模式。
     *
     * @return 校验模式
     */
    BizIdMode mode() default BizIdMode.CODE;

    /**
     * 最小长度。
     *
     * @return 字符串形式的最小长度
     */
    int min() default 1;

    /**
     * 最大长度。
     *
     * @return 字符串形式的最大长度
     */
    int max() default 64;

    /**
     * 自定义正则。
     *
     * @return {@link BizIdMode#CUSTOM} 模式使用的正则表达式
     */
    String regexp() default "";
}
