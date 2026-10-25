package com.sca4d.web.realtime.stomp;

import cn.hutool.crypto.digest.DigestUtil;
import com.sca4d.core.api.concurrent.RateLimit;
import com.sca4d.core.api.concurrent.RateLimitKey;
import com.sca4d.core.api.concurrent.RateLimiter;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** 声明 Web Realtime STOMP 各命令的限流额度和业务维度。 */
@ApiStatus.Internal
public class StompRateLimits {

    private static final RateLimit CONNECT = new RateLimit(20, Duration.ofSeconds(10));

    private static final RateLimit SUBSCRIBE = new RateLimit(120, Duration.ofSeconds(10));

    private static final RateLimit SEND = new RateLimit(60, Duration.ofSeconds(10));

    private final @NotNull RateLimiter limiter;

    /** 创建使用共享令牌桶限流器的 STOMP 协议限额策略。 */
    public StompRateLimits(@NotNull RateLimiter limiter) {
        this.limiter = Objects.requireNonNull(limiter, "令牌桶限流器不能为空");
    }

    /** @return 当前 IP 在 10 秒内是否仍有一次 CONNECT 额度 */
    boolean connect(@NotNull String ip) {
        return acquire("web-realtime.connect", List.of(key("ip", ip)), CONNECT);
    }

    /** @return 当前 Subject 与 Connection 在 10 秒内是否都仍有一次 SUBSCRIBE 额度 */
    boolean subscribe(long subjectId, @NotNull String connectionId) {
        return acquire(
                "web-realtime.subscribe",
                List.of(key("subject", Long.toString(subjectId)), key("connection", connectionId)),
                SUBSCRIBE);
    }

    /** @return 当前 Subject 与 Connection 在 10 秒内是否都仍有一次 SEND 额度 */
    boolean send(long subjectId, @NotNull String connectionId) {
        return acquire(
                "web-realtime.send",
                List.of(key("subject", Long.toString(subjectId)), key("connection", connectionId)),
                SEND);
    }

    /** 逐维消费令牌桶，并在全部维度判断完成后返回最终结果。 */
    private boolean acquire(String scope, List<String> keys, RateLimit limit) {
        boolean allowed = true;
        for (String key : keys) {
            if (!limiter.tryAcquire(new RateLimitKey(TreeCode.of("STOMP"), scope, key), limit).isAllowed()) {
                allowed = false;
            }
        }
        return allowed;
    }

    /** 使用摘要隐藏 Redis key 中的原始连接维度值。 */
    private static String key(String dimension, String value) {
        return dimension + "=" + DigestUtil.sha256Hex(value);
    }
}
