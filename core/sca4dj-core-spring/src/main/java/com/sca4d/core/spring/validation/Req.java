package com.sca4d.core.spring.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 为运行时输入提供必填 Bean Validation 约束，避免与 JetBrains 空值注解混用。 */
public class Req {

    /** 禁止创建校验注解容器实例。 */
    private Req() {
    }

    /** 要求输入值存在，允许空字符串和空集合。 */
    @Documented
    @jakarta.validation.constraints.NotNull
    @ReportAsSingleViolation
    @Constraint(validatedBy = {})
    @Target({
            ElementType.FIELD,
            ElementType.PARAMETER,
            ElementType.METHOD,
            ElementType.TYPE_USE,
            ElementType.ANNOTATION_TYPE
    })
    @Retention(RetentionPolicy.RUNTIME)
    public @interface NotNull {

        /**
         * 指定约束失败时交给 MessageSource 解析的文本。
         *
         * @return 校验失败消息模板
         */
        String message() default "{jakarta.validation.constraints.NotNull.message}";

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

    /** 要求输入文本存在且至少包含一个非空白字符。 */
    @Documented
    @jakarta.validation.constraints.NotBlank
    @ReportAsSingleViolation
    @Constraint(validatedBy = {})
    @Target({
            ElementType.FIELD,
            ElementType.PARAMETER,
            ElementType.METHOD,
            ElementType.TYPE_USE,
            ElementType.ANNOTATION_TYPE
    })
    @Retention(RetentionPolicy.RUNTIME)
    public @interface NotBlank {

        /**
         * 指定约束失败时交给 MessageSource 解析的文本。
         *
         * @return 校验失败消息模板
         */
        String message() default "{jakarta.validation.constraints.NotBlank.message}";

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

    /** 要求输入值存在且文本、集合、Map 或数组长度大于零。 */
    @Documented
    @jakarta.validation.constraints.NotEmpty
    @ReportAsSingleViolation
    @Constraint(validatedBy = {})
    @Target({
            ElementType.FIELD,
            ElementType.PARAMETER,
            ElementType.METHOD,
            ElementType.TYPE_USE,
            ElementType.ANNOTATION_TYPE
    })
    @Retention(RetentionPolicy.RUNTIME)
    public @interface NotEmpty {

        /**
         * 指定约束失败时交给 MessageSource 解析的文本。
         *
         * @return 校验失败消息模板
         */
        String message() default "{jakarta.validation.constraints.NotEmpty.message}";

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
}
