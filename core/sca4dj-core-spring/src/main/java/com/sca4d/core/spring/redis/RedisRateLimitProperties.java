package com.sca4d.core.spring.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 配置 Redis 限流状态在额度完全恢复后的额外保留时间。 */
@ConfigurationProperties("sca4dj.redis.rate-limit")
public class RedisRateLimitProperties {

    /** 额度完全恢复后继续保留限流状态的时间，避免频繁删除和重建 Redis 键。 */
    private Duration keepAfterRefill = Duration.ofHours(1);

    /**
     * 额度完全恢复后的额外保留时间。
     *
     * @return 正数保留时间
     */
    public Duration getKeepAfterRefill() {
        return keepAfterRefill;
    }

    /**
     * 设置额度完全恢复后的额外保留时间。
     *
     * @param keepAfterRefill 正数保留时间
     */
    public void setKeepAfterRefill(Duration keepAfterRefill) {
        if (keepAfterRefill == null || keepAfterRefill.isZero() || keepAfterRefill.isNegative()) {
            throw new IllegalArgumentException("限流状态额外保留时间必须为正数");
        }
        this.keepAfterRefill = keepAfterRefill;
    }
}
