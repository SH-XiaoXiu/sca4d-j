package com.sca4d.config.properties;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** HTTP 访问日志开关和按请求耗时选择 WARN、ERROR 等级的阈值配置。 */
@ConfigurationProperties(prefix = "sca4dj.request-log")
public class Sca4djRequestLogProperties {

    private boolean enabled = true;
    private Duration slowWarnThreshold = Duration.ofSeconds(1);
    private Duration slowErrorThreshold = Duration.ofSeconds(3);

    /** @return 是否输出独立 HTTP 访问日志 */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled 是否输出独立 HTTP 访问日志 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return 请求耗时达到该时长时使用 WARN 级别记录 */
    public @NotNull Duration getSlowWarnThreshold() {
        return slowWarnThreshold;
    }

    /**
     * 设置慢请求 WARN 阈值。
     *
     * @param slowWarnThreshold 从请求进入过滤器到响应完成的非负时长
     * @throws IllegalArgumentException 阈值为负数时抛出
     */
    public void setSlowWarnThreshold(@NotNull Duration slowWarnThreshold) {
        if (slowWarnThreshold.isNegative()) {
            throw new IllegalArgumentException("请求日志 WARN 阈值不能为负数");
        }
        this.slowWarnThreshold = slowWarnThreshold;
    }

    /** @return 请求耗时达到该时长或响应状态不低于 500 时使用 ERROR 级别记录 */
    public @NotNull Duration getSlowErrorThreshold() {
        return slowErrorThreshold;
    }

    /**
     * 设置慢请求 ERROR 阈值。
     *
     * @param slowErrorThreshold 从请求进入过滤器到响应完成的非负时长
     * @throws IllegalArgumentException 阈值为负数时抛出
     */
    public void setSlowErrorThreshold(@NotNull Duration slowErrorThreshold) {
        if (slowErrorThreshold.isNegative()) {
            throw new IllegalArgumentException("请求日志 ERROR 阈值不能为负数");
        }
        this.slowErrorThreshold = slowErrorThreshold;
    }
}
