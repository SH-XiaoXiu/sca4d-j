package com.sca4d.core.spring.redis;

import com.sca4d.core.api.utils.DurationUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 配置 Redis 幂等处理的处理中租约、建议重试时间和已完成结果保留时间。
 *
 * <p>三个配置项使用 Spring {@code Duration} 格式，例如 {@code 5m}、{@code 3s}、{@code 1d}；
 * 设置为空、零或负数时启动绑定失败。
 */
@ConfigurationProperties("sca4dj.redis.idempotency")
public class RedisIdempotencyProperties {

    /**
     * 配置键 {@code sca4dj.redis.idempotency.processing-ttl}，默认 5 分钟，取值必须为正数。
     * 节点中断后，处理中记录在该时间内阻止同一请求重复执行。
     */
    private Duration processingTtl = Duration.ofMinutes(5);

    /**
     * 配置键 {@code sca4dj.redis.idempotency.retry-after}，默认 3 秒，取值必须为正数。
     * 同一请求仍在处理中时，调用方收到的建议等待时间。
     */
    private Duration retryAfter = Duration.ofSeconds(3);

    /**
     * 配置键 {@code sca4dj.redis.idempotency.result-retention}，默认 1 天，取值必须为正数。
     * 幂等注解没有单独指定保留期时，已完成结果使用该时间。
     */
    private Duration resultRetention = Duration.ofDays(1);

    /**
     * 返回处理中租约。
     *
     * @return 正数处理租约
     */
    public Duration getProcessingTtl() {
        return processingTtl;
    }

    /**
     * 设置处理中租约。
     *
     * @param processingTtl 正数处理租约
     * @throws NullPointerException 值为 null 时抛出
     * @throws IllegalArgumentException 值为零或负数时抛出
     */
    public void setProcessingTtl(Duration processingTtl) {
        this.processingTtl = DurationUtils.requirePositive(processingTtl, "幂等处理租约");
    }

    /**
     * 返回建议重试时间。
     *
     * @return 正数建议重试时间
     */
    public Duration getRetryAfter() {
        return retryAfter;
    }

    /**
     * 设置建议重试时间。
     *
     * @param retryAfter 正数建议重试时间
     * @throws NullPointerException 值为 null 时抛出
     * @throws IllegalArgumentException 值为零或负数时抛出
     */
    public void setRetryAfter(Duration retryAfter) {
        this.retryAfter = DurationUtils.requirePositive(retryAfter, "幂等建议重试时间");
    }

    /**
     * 返回已完成结果的默认保留时间。
     *
     * @return 已完成结果默认保留时间
     */
    public Duration getResultRetention() {
        return resultRetention;
    }

    /**
     * 设置已完成结果的默认保留时间。
     *
     * @param resultRetention 已完成结果默认保留时间
     * @throws NullPointerException 值为 null 时抛出
     * @throws IllegalArgumentException 值为零或负数时抛出
     */
    public void setResultRetention(Duration resultRetention) {
        this.resultRetention = DurationUtils.requirePositive(resultRetention, "幂等结果默认保留期");
    }
}
