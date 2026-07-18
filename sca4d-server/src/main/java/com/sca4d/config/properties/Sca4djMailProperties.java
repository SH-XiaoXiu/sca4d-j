package com.sca4d.config.properties;

import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 动态 SMTP 连接使用的连接、读取和写入超时。 */
@ConfigurationProperties(prefix = "sca4dj.mail")
public class Sca4djMailProperties {

    private Duration connectionTimeout;
    private Duration readTimeout;
    private Duration writeTimeout;

    public @Nullable Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(@Nullable Duration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public @Nullable Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(@Nullable Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public @Nullable Duration getWriteTimeout() {
        return writeTimeout;
    }

    public void setWriteTimeout(@Nullable Duration writeTimeout) {
        this.writeTimeout = writeTimeout;
    }
}
