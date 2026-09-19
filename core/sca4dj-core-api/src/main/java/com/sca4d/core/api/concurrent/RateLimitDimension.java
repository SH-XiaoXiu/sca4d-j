package com.sca4d.core.api.concurrent;

/** 列出 `@RateLimited` 可以直接读取的调用限流维度。 */
public enum RateLimitDimension {
    /** 当前具体类型和完整方法签名的全部调用共享额度。 */
    GLOBAL,
    /** 按 HTTP 客户端 IP 分别计算额度。 */
    IP,
    /** 按 HTTP User-Agent 分别计算额度。 */
    UA,
    /** 按当前 `Subject` 分别计算额度。 */
    SUBJECT,
    /** 按当前 `Account` 分别计算额度。 */
    ACCOUNT
}
