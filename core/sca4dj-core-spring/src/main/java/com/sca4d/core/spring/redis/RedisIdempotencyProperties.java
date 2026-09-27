package com.sca4d.core.spring.redis;

import com.sca4d.core.api.utils.DurationUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 配置 Redis 幂等处理中记录的租约和调用方重试时间。 */
@ConfigurationProperties("sca4dj.redis.idempotency")
public class RedisIdempotencyProperties {

    /** 节点中断后处理中记录允许阻止重复执行的时间。 */
    private Duration processingTtl = Duration.ofMinutes(5);

    /** 同一请求仍在处理中时建议调用方等待的时间。 */
    private Duration retryAfter = Duration.ofSeconds(3);

    /** @return 正数处理租约 */
    public Duration getProcessingTtl() {
        return processingTtl;
    }

    /** @param processingTtl 正数处理租约 */
    public void setProcessingTtl(Duration processingTtl) {
        this.processingTtl = DurationUtils.requirePositive(processingTtl, "幂等处理租约");
    }

    /** @return 正数建议重试时间 */
    public Duration getRetryAfter() {
        return retryAfter;
    }

    /** @param retryAfter 正数建议重试时间 */
    public void setRetryAfter(Duration retryAfter) {
        this.retryAfter = DurationUtils.requirePositive(retryAfter, "幂等建议重试时间");
    }
}
