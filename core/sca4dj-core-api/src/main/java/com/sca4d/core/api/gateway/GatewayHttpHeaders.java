package com.sca4d.core.api.gateway;

/** 定义 Gateway 控制请求和可信转发信息使用的 HTTP Header。 */
public class GatewayHttpHeaders {

    /** Gateway 控制 Header 共用的名称前缀。 */
    public static final String PREFIX = "X-Gateway-";

    /** Gateway 控制 API 和 Server 可信转发校验共同使用的 Secret Header。 */
    public static final String SECRET = PREFIX + "Secret";

    /** Gateway 写入的原始客户端地址 Header。 */
    public static final String CLIENT_IP = PREFIX + "Client-Ip";

    /** Gateway 从公开请求路径移除的前缀 Header。 */
    public static final String EXTERNAL_PREFIX = PREFIX + "External-Prefix";

    /** RFC 7239 定义且客户端不能在可信转发链中自行声明的 Header。 */
    public static final String FORWARDED = "Forwarded";

    /** 常见代理转发 Header 共用且客户端不能在可信转发链中自行声明的名称前缀。 */
    public static final String FORWARDED_PREFIX = "X-Forwarded-";

    private GatewayHttpHeaders() {
    }
}
