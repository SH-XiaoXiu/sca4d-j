package com.sca4d.app.server.iot;

/** 定义示例应用的 Device HMAC 和 EMQX HTTP 请求使用的 Header。 */
public final class IotHttpHeaders {

    /** 示例应用 IoT 请求 Header 共用的名称前缀。 */
    public static final String PREFIX = "X-Iot-";

    /** Device HMAC 请求提交 Device 标识的 Header。 */
    public static final String DEVICE_ID = PREFIX + "Device-Id";

    /** Device HMAC 请求提交 Unix 秒时间戳的 Header。 */
    public static final String TIMESTAMP = PREFIX + "Timestamp";

    /** Device HMAC 请求提交一次性随机值的 Header。 */
    public static final String NONCE = PREFIX + "Nonce";

    /** Device HMAC 请求提交签名的 Header。 */
    public static final String SIGNATURE = PREFIX + "Signature";

    /** EMQX 端到端回调提交共享 Secret 的 Header。 */
    public static final String EMQX_SECRET = PREFIX + "Emqx-Secret";

    private IotHttpHeaders() {
    }
}
