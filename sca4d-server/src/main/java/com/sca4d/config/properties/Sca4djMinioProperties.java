package com.sca4d.config.properties;

import com.sca4d.common.boot.BootCheckPolicy;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** MinIO 配置。 */
@ConfigurationProperties(prefix = "sca4dj.minio")
public class Sca4djMinioProperties {

    private String endpoint = "http://localhost:9000";
    private String publicEndpoint = "http://localhost:9000";
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin";
    private String bucket = "sca4dj";
    private BootCheckPolicy startupCheck = BootCheckPolicy.required();

    public @NotNull String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(@NotNull String endpoint) {
        this.endpoint = endpoint;
    }

    public @NotNull String getPublicEndpoint() {
        return publicEndpoint;
    }

    public void setPublicEndpoint(@NotNull String publicEndpoint) {
        this.publicEndpoint = publicEndpoint;
    }

    public @NotNull String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(@NotNull String accessKey) {
        this.accessKey = accessKey;
    }

    public @NotNull String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(@NotNull String secretKey) {
        this.secretKey = secretKey;
    }

    public @NotNull String getBucket() {
        return bucket;
    }

    public void setBucket(@NotNull String bucket) {
        this.bucket = bucket;
    }

    /**
     * 获取 MinIO 启动检查策略。
     *
     * @return 启动检查策略
     */
    public @NotNull BootCheckPolicy getStartupCheck() {
        return startupCheck;
    }

    /**
     * 设置 MinIO 启动检查策略。
     *
     * @param startupCheck 启动检查策略
     */
    public void setStartupCheck(@NotNull BootCheckPolicy startupCheck) {
        this.startupCheck = startupCheck;
    }
}
