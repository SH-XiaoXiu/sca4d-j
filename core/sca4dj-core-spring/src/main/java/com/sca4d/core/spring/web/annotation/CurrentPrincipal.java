package com.sca4d.core.spring.web.annotation;

import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.spring.identity.PrincipalSelection;
import io.swagger.v3.oas.annotations.Parameter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 将当前已经选择的 {@link Principal} 注入 Controller 参数。 */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@CallLifecycle.Intercepted
@Parameter(hidden = true)
public @interface CurrentPrincipal {

    /**
     * 判断未确定业务身份时是否允许参数注入空值。
     *
     * @return 允许注入空值时返回 {@code true}
     */
    boolean nullable() default false;

    /**
     * 判断 Controller 执行前是否必须确定业务身份。
     *
     * @return 必须确定业务身份时返回 {@code true}
     */
    boolean principalRequired() default true;

    /**
     * 返回当前接口使用的业务身份选择器类型。
     *
     * @return 当前接口专用选择器；默认使用容器中的唯一通用选择器
     */
    Class<? extends PrincipalSelection> selection() default PrincipalSelection.class;
}
