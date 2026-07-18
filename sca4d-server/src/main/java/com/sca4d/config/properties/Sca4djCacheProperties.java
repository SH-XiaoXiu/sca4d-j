package com.sca4d.config.properties;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 缓存配置。
 */
@ConfigurationProperties(prefix = "sca4dj.cache")
public class Sca4djCacheProperties {

    private String keyPrefix = "sca4dj";
    private Duration redisTtl = Duration.ofMinutes(30);
    private Duration localTtl = Duration.ofMinutes(5);
    private long localMaximumSize = 1000;

    public @NotNull String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(@NotNull String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public @NotNull Duration getRedisTtl() {
        return redisTtl;
    }

    public void setRedisTtl(@NotNull Duration redisTtl) {
        this.redisTtl = redisTtl;
    }

    public @NotNull Duration getLocalTtl() {
        return localTtl;
    }

    public void setLocalTtl(@NotNull Duration localTtl) {
        this.localTtl = localTtl;
    }

    public long getLocalMaximumSize() {
        return localMaximumSize;
    }

    public void setLocalMaximumSize(long localMaximumSize) {
        this.localMaximumSize = localMaximumSize;
    }
}
