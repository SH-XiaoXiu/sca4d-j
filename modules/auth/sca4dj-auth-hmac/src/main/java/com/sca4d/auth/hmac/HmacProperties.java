package com.sca4d.auth.hmac;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 保存 HMAC 认证的时间窗、nonce 和失败限制。 */
@ConfigurationProperties("sca4dj.auth.hmac")
public final class HmacProperties {

    private Duration clockSkew = Duration.ofMinutes(5);

    private Duration nonceTtl = Duration.ofMinutes(10);

    private Duration failureWindow = Duration.ofMinutes(1);

    private int failureLimit = 10;

    /** @return 允许的最大客户端时钟偏差 */
    public Duration getClockSkew() {
        return clockSkew;
    }

    /** @param clockSkew 允许的正数时钟偏差 */
    public void setClockSkew(Duration clockSkew) {
        this.clockSkew = clockSkew;
    }

    /** @return 已消费 nonce 保留时间 */
    public Duration getNonceTtl() {
        return nonceTtl;
    }

    /** @param nonceTtl 已消费 nonce 的正数保留时间 */
    public void setNonceTtl(Duration nonceTtl) {
        this.nonceTtl = nonceTtl;
    }

    /** @return 失败计数窗口 */
    public Duration getFailureWindow() {
        return failureWindow;
    }

    /** @param failureWindow 失败计数的正数窗口 */
    public void setFailureWindow(Duration failureWindow) {
        this.failureWindow = failureWindow;
    }

    /** @return 一个窗口允许的失败次数 */
    public int getFailureLimit() {
        return failureLimit;
    }

    /** @param failureLimit 1 到 1000 的失败次数上限 */
    public void setFailureLimit(int failureLimit) {
        this.failureLimit = failureLimit;
    }

    /** 校验自动配置创建认证方法前需要的全部限制。 */
    public void validate() {
        positive(clockSkew, "sca4dj.auth.hmac.clock-skew");
        positive(nonceTtl, "sca4dj.auth.hmac.nonce-ttl");
        positive(failureWindow, "sca4dj.auth.hmac.failure-window");
        if (failureLimit < 1 || failureLimit > 1000) {
            throw new IllegalStateException("sca4dj.auth.hmac.failure-limit 必须在 1 到 1000 之间");
        }
    }

    /** 要求认证时间配置为正数。 */
    private static void positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(name + " 必须为正数时长");
        }
    }
}
