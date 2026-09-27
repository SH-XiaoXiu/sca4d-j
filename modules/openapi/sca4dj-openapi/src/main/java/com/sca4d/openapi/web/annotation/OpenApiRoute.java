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
 * <p>`OpenApiRoute` 组合 Spring MVC {@link RequestMapping}。具体应用只声明一次 HTTP 方法和路径，Spring MVC 负责注册路由，OpenAPI
 * 模块负责校验 Operation、执行 Client 认证和生成独立的运行时文档分组。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@RequestMapping
public @interface OpenApiRoute {

    /** @return 当前路由接受的唯一 HTTP 方法 */
    @AliasFor(annotation = RequestMapping.class, attribute = "method")
    RequestMethod[] method();

    /** @return 完整的公开路径，必须以 {@code /openapi/} 开头 */
    @AliasFor(annotation = RequestMapping.class, attribute = "path")
    String[] path();

    /** @return 请求体允许的媒体类型；没有请求体时保持为空 */
    @AliasFor(annotation = RequestMapping.class, attribute = "consumes")
    String[] consumes() default {};

    /** @return 响应媒体类型，默认只返回 JSON */
    @AliasFor(annotation = RequestMapping.class, attribute = "produces")
    String[] produces() default MediaType.APPLICATION_JSON_VALUE;

    /** @return 第三方 Tool 和 Swagger 使用的稳定分组名称 */
    String toolGroup();

    /** @return Tool 分组内的稳定名称 */
    String toolName();

    /** @return OperationCatalog 中已经注册的固定 Operation 编码 */
    String operation();
}
