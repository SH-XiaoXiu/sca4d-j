package com.sca4d.core.api.concurrent;

/** 列出幂等请求开始处理时可能得到的互斥状态。 */
public enum IdempotencyBeginStatus {
    /** 当前请求已经取得处理所有权。 */
    ACQUIRED,
    /** 已存在同一请求可以直接重放的成功结果。 */
    REPLAY,
    /** 同一请求仍由其他执行者处理。 */
    IN_PROGRESS,
    /** 同一幂等键已经绑定不同请求指纹。 */
    CONFLICT
}
