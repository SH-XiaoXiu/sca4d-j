package com.sca4d.core.spring.web.annotation;

import com.sca4d.core.api.concurrent.Version;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 HTTP 响应数据中用于生成强 ETag 的 {@link Version} 方法。
 *
 * <p>一个响应数据类型最多声明一个公开、无参数且返回 {@link Version} 的方法。返回的版本必须覆盖顶层
 * {@code ApiResponse.data} 当前实例的全部可观察字段；同一类型同时用于稳定资源和动态投影时，动态实例返回
 * {@code null}，Core Spring 不发布 ETag。集合元素和短期访问地址不使用本注解。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ETag {
}
