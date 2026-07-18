package com.sca4d.common.web;

/**
 * Web 常量。
 */
public final class WebConstants {

    /** 成功响应码。 */
    public static final String SUCCESS_CODE = "0";

    /** 成功响应消息。 */
    public static final String SUCCESS_MESSAGE = "OK";

    /** Trace ID 响应头。 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** 幂等响应是否来自历史成功结果重放。 */
    public static final String IDEMPOTENCY_REPLAYED_HEADER = "Idempotency-Replayed";

    private WebConstants() {
    }
}
