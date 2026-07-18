package com.sca4d.common.auth.annotation;

import com.sca4d.common.auth.PrincipalSelection;
import com.sca4d.common.auth.PrincipalType;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明或注入当前执行主体。
 *
 * <p>用于类或方法时表示该接口需要以指定 Principal 类型执行；用于参数时可注入 Principal 上下文、ID 或类型。
 */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Parameter(hidden = true)
public @interface AuthPrincipal {

    /**
     * 当前接口允许的主体类型。
     *
     * @return 允许的主体类型集合
     */
    @AliasFor("types")
    PrincipalType[] value() default {};

    /**
     * 当前接口允许的主体类型，与 {@link #value()} 互为别名。
     *
     * @return 允许的主体类型集合
     */
    @AliasFor("value")
    PrincipalType[] types() default {};

    /**
     * 是否允许当前主体为空。仅参数注入时生效。
     *
     * @return true 表示缺少主体时注入 null；false 表示缺少主体时抛出异常
     */
    boolean nullable() default false;

    /**
     * 是否必须解析出 Principal。仅类或方法声明时生效。
     *
     * @return true 表示接口进入前必须解析 Principal
     */
    boolean principalRequired() default true;

    /**
     * 接口专用的 PrincipalSelection 实现。
     *
     * @return PrincipalSelection 类型；默认使用 Spring 容器中的通用实现
     */
    Class<? extends PrincipalSelection> selection() default PrincipalSelection.class;

    /**
     * Principal ID 选择表达式。
     *
     * @return SpEL 表达式；为空表示不显式指定 Principal ID
     */
    String principalId() default "";

    /**
     * Tenant ID 选择表达式。
     *
     * @return SpEL 表达式；为空表示不显式指定 Tenant ID
     */
    String tenantId() default "";

    /**
     * 业务选择键表达式。
     *
     * @return SpEL 表达式；为空表示不显式指定业务选择键
     */
    String selectionKey() default "";
}
