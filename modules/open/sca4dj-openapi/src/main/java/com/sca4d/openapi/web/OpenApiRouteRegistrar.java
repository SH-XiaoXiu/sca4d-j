package com.sca4d.openapi.web;

import org.jetbrains.annotations.NotNull;

/**
 * 在应用启动时向 OpenAPI 路由目录登记路由元数据。
 *
 * <p>注册器可以由具体应用提供，也可以由 {@code @OpenApiRoute} 适配器提供；全部注册完成后目录会冻结。
 */
@FunctionalInterface
public interface OpenApiRouteRegistrar {

    /**
     * 登记当前实现负责的全部路由。
     *
     * @param routes 尚未冻结的 OpenAPI 路由目录
     */
    void register(@NotNull OpenApiRouteCatalog routes);
}
