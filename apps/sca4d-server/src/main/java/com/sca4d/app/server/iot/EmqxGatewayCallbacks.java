package com.sca4d.app.server.iot;

import com.sca4d.core.api.gateway.GatewayCallbackCode;

/** 定义示例应用向 Gateway 注册的 EMQX 回调编码。 */
public final class EmqxGatewayCallbacks {

    /** 示例应用 EMQX 回调编码共用的命名空间。 */
    public static final String PREFIX = "iot.emqx.";

    /** EMQX HTTP Authenticator 的 MQTT CONNECT 认证回调。 */
    public static final GatewayCallbackCode AUTHENTICATION = new GatewayCallbackCode(PREFIX + "authenticate");

    private EmqxGatewayCallbacks() {
    }
}
