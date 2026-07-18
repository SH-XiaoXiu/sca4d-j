package com.sca4d.config.properties;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** IAM 终态关系清理的 Quartz 触发间隔。 */
@ConfigurationProperties(prefix = "sca4dj.iam")
public class Sca4djIamCleanupProperties {

    private Duration cleanupInterval;

    /** @return Subject 与 Tenant 关系清理触发间隔 */
    public @NotNull Duration getCleanupInterval() {
        return cleanupInterval;
    }

    /** @param cleanupInterval Subject 与 Tenant 关系清理触发间隔 */
    public void setCleanupInterval(@NotNull Duration cleanupInterval) {
        if (cleanupInterval.isZero() || cleanupInterval.isNegative() || cleanupInterval.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("IAM 清理间隔必须为正数且不超过 1 天");
        }
        this.cleanupInterval = cleanupInterval;
    }
}
