package com.sca4d.common.permission.annotation;

import com.sca4d.common.access.AccessAction;
import com.sca4d.common.access.ResourceAccessPolicy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 声明当前接口使用的业务模块资源访问策略。 */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Access.List.class)
public @interface Access {

    /**
     * 当前资源所属的业务模块访问策略。
     *
     * @return Spring 容器中的 Policy 类型
     */
    Class<? extends ResourceAccessPolicy<?, ?>> policy();

    /**
     * 当前业务动作需要的固定资源访问等级。
     *
     * @return READ、WRITE 或 MANAGE
     */
    AccessAction action();

    /**
     * 可选单资源标识表达式。
     *
     * @return 支持 path、query、header、body、payload 和方法参数的 SpEL；空值表示集合范围
     */
    String resourceId() default "";

    /** 可重复资源访问声明容器。 */
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {

        /**
         * 返回同一声明层级的资源访问策略集合。
         *
         * @return 资源访问声明集合
         */
        Access[] value();
    }
}
