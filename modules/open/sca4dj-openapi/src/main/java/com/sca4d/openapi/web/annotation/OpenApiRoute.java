package com.sca4d.openapi.web.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明一条由具体应用明确公开的 OpenAPI Tool 路由。
 *
 * <p>{@code OpenApiRoute} 组合 Spring MVC {@link RequestMapping}，并由启动期
 * {@code OpenApiRouteAnnotationRegistrar} 转换为 {@code OpenApiRouteDefinition}。它是应用声明开放路由的便捷方式；应用也可以使用
 * {@code OpenApiRouteRegistrar} 直接登记路由元数据。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@RequestMapping
public @interface OpenApiRoute {

    /** @return 当前路由接受的唯一 HTTP 方法 */
    @AliasFor(annotation = RequestMapping.class, attribute = "method")
    RequestMethod[] method();

    /** @return 完整的公开路径，必须位于配置的 {@code sca4dj.openapi.path-prefix} 之下 */
    @AliasFor(annotation = RequestMapping.class, attribute = "path")
    String[] path();

    /** @return 请求体允许的媒体类型；没有请求体时保持为空 */
    @AliasFor(annotation = RequestMapping.class, attribute = "consumes")
    String[] consumes() default {};

    /** @return 响应媒体类型，默认只返回 JSON */
    @AliasFor(annotation = RequestMapping.class, attribute = "produces")
    String[] produces() default MediaType.APPLICATION_JSON_VALUE;

    /** @return 第三方 Tool 与 Swagger 文档使用的稳定分组标识 */
    String toolGroup();

    /** @return Tool 分组内的稳定标识 */
    String toolName();

    /** @return OperationCatalog 中已经注册的固定 Operation 编码 */
    String operation();
}
