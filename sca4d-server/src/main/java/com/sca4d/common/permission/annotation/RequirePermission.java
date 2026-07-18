package com.sca4d.common.permission.annotation;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明当前接口需要满足的角色和功能权限。
 *
 * <p>同一声明中的所有条件均按 AND 执行。声明必须至少包含一个明确的权限或角色编码，禁止空白编码和
 * 通配符 {@code *}。
 */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /**
     * 功能权限编码。
     *
     * @return 权限编码集合
     */
    @AliasFor("permissions")
    String[] value() default {};

    /**
     * 功能权限编码，与 {@link #value()} 互为别名。
     *
     * @return 权限编码集合
     */
    @AliasFor("value")
    String[] permissions() default {};

    /**
     * 角色编码。
     *
     * @return 角色编码集合
     */
    String[] roles() default {};

}
