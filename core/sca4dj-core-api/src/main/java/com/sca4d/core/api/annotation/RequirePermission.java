package com.sca4d.core.api.annotation;

import com.sca4d.core.api.call.CallValueResolver;
import com.sca4d.core.api.call.NoCallValueResolver;
import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.core.api.resource.Resource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collection;

/**
 * 声明调用必须具有一组资源权限（Required Permission）。
 *
 * <p>{@link #operation()} 与 {@link #resourceResolver()} 必须二选一。固定操作生成 {@link Operation} 资源；动态解析器必须返回
 * 非空 {@link Collection}{@code <Resource>}。单个资源使用只包含一个元素的集合。注解可重复，Core 会展开后逐项判断。
 */
@Documented
@Repeatable(RequirePermissions.class)
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /**
     * 返回固定操作键；使用动态资源时保持空字符串。
     *
     * @return 固定操作键；使用动态资源时保持空字符串
     */
    String operation() default "";

    /**
     * 返回动态业务资源集合解析器；使用固定操作时保持占位类型。
     *
     * @return 动态业务资源集合解析器；使用固定操作时保持占位类型
     */
    Class<? extends CallValueResolver<? extends Collection<Resource>>> resourceResolver() default NoCallValueResolver.class;

    /**
     * 返回动作键；固定操作为空时使用 {@link Action#INVOKE}，动态资源必须明确填写。
     *
     * @return 动作键；固定操作为空时使用 {@link Action#INVOKE}，动态资源必须明确填写
     */
    String action() default "";
}
