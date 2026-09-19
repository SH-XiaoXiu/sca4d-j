package com.sca4d.core.spring.gateway;

import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/** 读取已经由 `TrustedGatewayRequestFilter` 验证的外部请求路径和客户端地址。 */
public final class TrustedGatewayRequest {

    static final String EXTERNAL_PATH_ATTRIBUTE = TrustedGatewayRequest.class.getName() + ".externalPath";

    static final String CLIENT_IP_ATTRIBUTE = TrustedGatewayRequest.class.getName() + ".clientIp";

    private TrustedGatewayRequest() {
    }

    /**
     * 返回客户端签名和审计应采用的外部请求路径。
     *
     * @param request 当前 Servlet 请求
     * @return 可信 Gateway 提供的外部路径；直接访问应用时返回 Servlet 请求路径
     */
    @Contract(pure = true)
    public static @NotNull String externalPath(@NotNull HttpServletRequest request) {
        Object value = request.getAttribute(EXTERNAL_PATH_ATTRIBUTE);
        return value instanceof String path ? path : request.getRequestURI();
    }

    /**
     * 返回审计应采用的客户端地址。
     *
     * @param request 当前 Servlet 请求
     * @return 可信 Gateway 提供的首个转发地址；直接访问应用时返回连接地址
     */
    @Contract(pure = true)
    public static @NotNull String clientIp(@NotNull HttpServletRequest request) {
        Object value = request.getAttribute(CLIENT_IP_ATTRIBUTE);
        return value instanceof String clientIp ? clientIp : request.getRemoteAddr();
    }
}
