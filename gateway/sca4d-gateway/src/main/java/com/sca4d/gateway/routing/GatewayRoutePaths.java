package com.sca4d.gateway.routing;

import com.sca4d.core.api.setting.ConfiguredConstants;

/** 保存 Gateway 内置长连接路由在启动时确定的应用内路径。 */
public final class GatewayRoutePaths {

    /** SSE 专用路由在公开 API 前缀之后匹配的路径。 */
    public static final String SSE = ConfiguredConstants.text("sca4dj.gateway.routes.sse-path", "/events");

    /** WebSocket 专用路由在公开 API 前缀之后匹配的路径。 */
    public static final String WEBSOCKET = ConfiguredConstants.text(
            "sca4dj.gateway.routes.websocket-path",
            "/events/ws");

    private GatewayRoutePaths() {
    }

    /** 校验两条内置路由不会匹配同一路径。 */
    public static void validate() {
        for (String path : new String[] {SSE, WEBSOCKET}) {
            if (!path.startsWith("/")
                    || path.length() == 1
                    || path.endsWith("/")
                    || path.contains("//")
                    || path.contains("?")
                    || path.contains("#")) {
                throw new IllegalStateException("Gateway 长连接路由路径无效: " + path);
            }
        }
        if (SSE.equals(WEBSOCKET)) {
            throw new IllegalStateException("Gateway SSE 和 WebSocket 路由不能相同");
        }
    }
}
