package com.sca4d.core.spring.validation;

import com.sca4d.core.api.region.Continent;
import com.sca4d.core.api.region.PhoneNumberCondition;
import com.sca4d.core.api.region.PhoneNumberType;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 校验规范 E.164 电话号码及 libphonenumber 元数据，不表达号码已分配、归属或可以接通。
 *
 * <p>空白值视为未提供；需要必填时组合使用 Req.NotBlank。每个 Rule 内的非空条件必须同时满足，anyOf 中任意 Rule 满足即可。
 */
@Documented
@Constraint(validatedBy = PhoneNumberValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PhoneNumber {

    /**
     * 指定约束失败时交给 MessageSource 解析的文本。
     *
     * @return 校验失败消息模板
     */
    String message() default "{validation.phone-number.invalid}";

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
     * 指定号码至少需要满足的一组规则。
     *
     * @return 以 OR 连接的规则；空数组接受任意有效 E.164 号码
     */
    Rule[] anyOf() default {};

    /** 声明一个必须同时满足全部包含条件且不满足任何排除条件的号码规则。 */
    @Documented
    @Target({})
    @Retention(RetentionPolicy.RUNTIME)
    @interface Rule {

        /**
         * 限制号码可以属于的 libphonenumber 地区。
         *
         * @return 允许的地区代码；空数组不限制地区
         */
        String[] regions() default {};

        /**
         * 限制地理号码可以属于的洲别。
         *
         * @return 允许的洲别；空数组不限制洲别
         */
        Continent[] continents() default {};

        /**
         * 限制号码可以使用的国家或地区拨号前缀。
         *
         * @return 允许的正整数国际电话代码；空数组不限制
         */
        int[] countryCallingCodes() default {};

        /**
         * 限制 libphonenumber 判断的号码用途。
         *
         * @return 允许的电话号码类型；空数组不限制类型
         */
        PhoneNumberType[] types() default {};

        /**
         * 排除即使被允许条件包含也不能通过的地区。
         *
         * @return 明确排除的 libphonenumber 地区代码
         */
        String[] excludeRegions() default {};

        /**
         * 排除即使被允许条件包含也不能通过的洲别。
         *
         * @return 明确排除的洲别
         */
        Continent[] excludeContinents() default {};

        /**
         * 排除即使被允许条件包含也不能通过的拨号前缀。
         *
         * @return 明确排除的正整数国际电话代码
         */
        int[] excludeCountryCallingCodes() default {};

        /**
         * 排除即使被允许条件包含也不能通过的号码用途。
         *
         * @return 明确排除的电话号码类型
         */
        PhoneNumberType[] excludeTypes() default {};

        /**
         * 增加地区元数据之外必须逐个执行的检查。
         *
         * @return 必须全部通过的 PhoneNumberCondition Bean 类型
         */
        Class<? extends PhoneNumberCondition>[] conditions() default {};
    }
}
