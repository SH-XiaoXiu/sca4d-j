package com.sca4d.core.api.gateway;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

/**
 * 声明一个可由 Gateway 按编码路由的 HTTP 回调。
 *
 * @param <I> JSON 请求体转换后的输入类型
 * @param <O> JSON 响应体类型
 */
public interface GatewayCallback<I, O> {

    /** @return Gateway 注册和选择实例时使用的回调编码 */
    @CheckReturnValue
    @NotNull
    GatewayCallbackCode code();

    /** @return HTTP JSON 请求体必须转换成的精确类型 */
    @CheckReturnValue
    @NotNull
    Class<I> requestType();

    /**
     * 执行回调并给出原始 HTTP 结果。
     *
     * @param request 已完成 JSON 类型转换并保留请求 Header 的回调请求
     * @return HTTP 状态、响应 Header 和可选 JSON 响应体
     */
    @CheckReturnValue
    @NotNull
    GatewayCallbackResponse<O> respond(@NotNull GatewayCallbackRequest<I> request);
}
