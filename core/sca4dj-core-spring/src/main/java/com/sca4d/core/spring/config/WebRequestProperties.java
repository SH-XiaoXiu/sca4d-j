package com.sca4d.core.spring.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

/** 配置整个 Servlet 应用的慢 HTTP 请求判定阈值。 */
@ConfigurationProperties("sca4dj.web")
public class WebRequestProperties {

    /** 请求耗时达到该值时由 `HttpCallFilter` 记录 WARN 级慢请求日志，默认 200 毫秒。 */
    private @NotNull Duration slowRequestThreshold = Duration.ofMillis(200);

    /** @return 至少 1 毫秒且可表示为 long 纳秒数的阈值 */
    public @NotNull Duration getSlowRequestThreshold() {
        return slowRequestThreshold;
    }

    /**
     * @param slowRequestThreshold 至少 1 毫秒的慢请求阈值
     * @throws IllegalArgumentException 阈值小于 1 毫秒或无法表示为 long 纳秒数时抛出
     */
    public void setSlowRequestThreshold(@NotNull Duration slowRequestThreshold) {
        Duration checked = Objects.requireNonNull(slowRequestThreshold, "慢请求阈值不能为空");
        if (checked.compareTo(Duration.ofMillis(1)) < 0) {
            throw new IllegalArgumentException("慢请求阈值必须至少为 1 毫秒");
        }
        try {
            checked.toNanos();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("慢请求阈值超过纳秒计时范围", exception);
        }
        this.slowRequestThreshold = checked;
    }
}
