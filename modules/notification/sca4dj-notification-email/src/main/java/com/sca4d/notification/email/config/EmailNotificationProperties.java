package com.sca4d.notification.email.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 配置 SMTP 建连、读取和写入超时，避免异步 Consumer 无限阻塞。 */
@ConfigurationProperties("sca4dj.notification.email")
public class EmailNotificationProperties {

    private Duration connectionTimeout = Duration.ofSeconds(5);

    private Duration readTimeout = Duration.ofSeconds(10);

    private Duration writeTimeout = Duration.ofSeconds(10);

    private long maxAttachmentBytes = 20L * 1024 * 1024;

    public EmailNotificationProperties() {
    }

    /** @return SMTP 建连超时 */
    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    /** @param connectionTimeout 正数 SMTP 建连超时 */
    public void setConnectionTimeout(Duration connectionTimeout) {
        this.connectionTimeout = requirePositive(connectionTimeout, "SMTP 建连超时");
    }

    /** @return SMTP 读取超时 */
    public Duration getReadTimeout() {
        return readTimeout;
    }

    /** @param readTimeout 正数 SMTP 读取超时 */
    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = requirePositive(readTimeout, "SMTP 读取超时");
    }

    /** @return SMTP 写入超时 */
    public Duration getWriteTimeout() {
        return writeTimeout;
    }

    /** @param writeTimeout 正数 SMTP 写入超时 */
    public void setWriteTimeout(Duration writeTimeout) {
        this.writeTimeout = requirePositive(writeTimeout, "SMTP 写入超时");
    }

    /** @return 单封同步 Email 附件允许的总字节数 */
    public long getMaxAttachmentBytes() {
        return maxAttachmentBytes;
    }

    /** @param maxAttachmentBytes 单封同步 Email 附件允许的正数总字节数 */
    public void setMaxAttachmentBytes(long maxAttachmentBytes) {
        if (maxAttachmentBytes < 1) {
            throw new IllegalArgumentException("Email 附件总大小上限必须为正数");
        }
        this.maxAttachmentBytes = maxAttachmentBytes;
    }

    /** 校验超时为正数并适合转换成 JavaMail 毫秒配置。 */
    private static Duration requirePositive(Duration value, String label) {
        if (value == null || value.isZero() || value.isNegative() || value.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(label + "必须为正数且不超过 " + Integer.MAX_VALUE + " 毫秒");
        }
        return value;
    }
}
