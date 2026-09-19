package com.sca4d.core.api.call;

/**
 * 根据当前方法执行解析注解声明的动态值。
 *
 * @param <T> 解析值类型
 */
@FunctionalInterface
public interface CallValueResolver<T> {

    /**
     * 解析动态值。
     *
     * @param execution 当前方法执行
     * @return 解析后的非空值
     */
    T resolve(CallExecution execution);
}
