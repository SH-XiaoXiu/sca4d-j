package com.sca4d.core.api.gateway;

/** 定义 Gateway 与应用实例共同使用的稳定 HTTP 路径。 */
public final class GatewayHttpPaths {

    /** 外部调用 Server API 时使用的公开路径前缀。 */
    public static final String API_PREFIX = "/api";

    /** Gateway 匹配全部公开 Server API 时使用的路径模式。 */
    public static final String API_PATTERN = API_PREFIX + "/**";

    /** Gateway 回调的路径根。 */
    public static final String CALLBACKS = "/callbacks";

    /** Gateway 回调编码前的路径前缀。 */
    public static final String CALLBACK_PREFIX = CALLBACKS + "/";

    /** Gateway 匹配单段回调编码时使用的路径模式。 */
    public static final String CALLBACK_PATTERN = CALLBACK_PREFIX + "{code}";

    /** Server 实例注册、续租和注销使用的控制 API 路径根。 */
    public static final String REGISTRATIONS = "/internal/gateway/registrations";

    /** Gateway 控制 API 匹配单个 Server 实例租约时使用的路径模式。 */
    public static final String REGISTRATION_LEASE_PATTERN = "/{instanceId}/leases/{leaseId}";

    private GatewayHttpPaths() {
    }
}
