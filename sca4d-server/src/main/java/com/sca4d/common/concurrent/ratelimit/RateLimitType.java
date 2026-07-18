package com.sca4d.common.concurrent.ratelimit;

/**
 * 限流维度。
 */
public enum RateLimitType {
    /** 全局接口维度。 */
    GLOBAL,
    /** 客户端 IP 维度。 */
    IP,
    /** 当前用户维度，优先 Principal，其次 Subject。 */
    USER,
    /** User-Agent 维度。 */
    UA,
    /** 当前长连接维度。 */
    CONNECTION,
    /** 自定义维度。 */
    CUSTOM
}
