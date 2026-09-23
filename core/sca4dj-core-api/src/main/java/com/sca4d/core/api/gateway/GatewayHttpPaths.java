package com.sca4d.core.api.gateway;

/** 定义 Gateway 与应用实例共同使用且不能由单侧配置改变的内部 HTTP 路径。 */
public final class GatewayHttpPaths {

    /** Server 内部回调分发器的路径根。 */
    public static final String CALLBACKS = "/callbacks";

    /** Server 内部回调编码前的路径前缀。 */
    public static final String CALLBACK_PREFIX = CALLBACKS + "/";

    /** Server 实例注册、续租和注销使用的控制 API 路径根。 */
    public static final String REGISTRATIONS = "/internal/gateway/registrations";

    /** Gateway 控制 API 匹配单个 Server 实例租约时使用的路径模式。 */
    public static final String REGISTRATION_LEASE_PATTERN = "/{instanceId}/leases/{leaseId}";

    private GatewayHttpPaths() {
    }
}
