package com.sca4d.storage.minio;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.Objects;

/** 配置 MinIO 的 backendId、内部端点、公开端点、凭据和 Bucket。 */
@ConfigurationProperties("sca4dj.storage.minio")
public class MinioStorageProperties {
    private boolean enabled = true;

    private @NotNull String backendId = "minio";

    private @NotNull String endpoint = "http://localhost:9000";

    private @NotNull String publicEndpoint = "http://localhost:9000";

    private @NotNull String region = "us-east-1";

    private @NotNull String accessKey = "minioadmin";

    private @NotNull String secretKey = "minioadmin";

    private @NotNull String bucket = "sca4dj";

    private boolean createBucket = true;

    /** 创建使用本地开发默认端点和 Bucket 的配置。 */
    public MinioStorageProperties() {
    }

    /**
     * 返回是否注册 MinIO 存储实现。
     *
     * @return 启用时为 {@code true}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否注册 MinIO 存储实现。
     *
     * @param enabled 启用时为 {@code true}
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回写入文件记录的存储实现标识。
     *
     * @return 非空白存储实现标识
     */
    public @NotNull String getBackendId() {
        return backendId;
    }

    /**
     * 设置写入文件记录的存储实现标识。
     *
     * @param backendId 非空白存储实现标识
     */
    public void setBackendId(@NotNull String backendId) {
        this.backendId = requireText(backendId, "MinIO 存储实现标识");
    }

    /**
     * 返回服务端访问 MinIO 的内部端点。
     *
     * @return 包含协议和主机且不带路径的内部端点
     */
    public @NotNull String getEndpoint() {
        return endpoint;
    }

    /**
     * 设置服务端访问 MinIO 的内部端点。
     *
     * @param endpoint 包含协议和主机且不带路径的内部端点
     */
    public void setEndpoint(@NotNull String endpoint) {
        this.endpoint = requireOrigin(endpoint, "MinIO 内部端点");
    }

    /**
     * 返回写入预签名 URL 的公开端点。
     *
     * @return 客户端能够访问且不带路径的公开端点
     */
    public @NotNull String getPublicEndpoint() {
        return publicEndpoint;
    }

    /**
     * 设置写入预签名 URL 的公开端点。
     *
     * @param publicEndpoint 客户端能够访问且不带路径的公开端点
     */
    public void setPublicEndpoint(@NotNull String publicEndpoint) {
        this.publicEndpoint = requireOrigin(publicEndpoint, "MinIO 公开端点");
    }

    /**
     * 返回签名和对象请求使用的区域。
     *
     * @return 非空白区域名称
     */
    public @NotNull String getRegion() {
        return region;
    }

    /**
     * 设置签名和对象请求使用的区域。
     *
     * @param region 非空白区域名称
     */
    public void setRegion(@NotNull String region) {
        this.region = requireText(region, "MinIO Region");
    }

    /**
     * 返回 MinIO 客户端使用的访问密钥标识。
     *
     * @return 非空且不含控制字符的访问密钥标识
     */
    public @NotNull String getAccessKey() {
        return accessKey;
    }

    /**
     * 设置 MinIO 客户端使用的访问密钥标识。
     *
     * @param accessKey 非空且不含控制字符的访问密钥标识
     */
    public void setAccessKey(@NotNull String accessKey) {
        this.accessKey = requireCredential(accessKey, "MinIO Access Key");
    }

    /**
     * 返回 MinIO 客户端用于签名的密钥。
     *
     * @return 非空且不含控制字符的签名密钥
     */
    public @NotNull String getSecretKey() {
        return secretKey;
    }

    /**
     * 设置 MinIO 客户端用于签名的密钥。
     *
     * @param secretKey 非空且不含控制字符的签名密钥
     */
    public void setSecretKey(@NotNull String secretKey) {
        this.secretKey = requireCredential(secretKey, "MinIO Secret Key");
    }

    /**
     * 返回保存文件对象的 Bucket 名称。
     *
     * @return 非空白 Bucket 名称
     */
    public @NotNull String getBucket() {
        return bucket;
    }

    /**
     * 设置保存文件对象的 Bucket 名称。
     *
     * @param bucket 非空白 Bucket 名称
     */
    public void setBucket(@NotNull String bucket) {
        this.bucket = requireText(bucket, "MinIO Bucket");
    }

    /**
     * 返回启动检查是否可以创建缺失的 Bucket。
     *
     * @return 允许创建时为 {@code true}
     */
    public boolean isCreateBucket() {
        return createBucket;
    }

    /**
     * 设置启动检查是否可以创建缺失的 Bucket。
     *
     * @param createBucket 允许创建时为 {@code true}
     */
    public void setCreateBucket(boolean createBucket) {
        this.createBucket = createBucket;
    }

    private static @NotNull String requireOrigin(@NotNull String value, @NotNull String name) {
        String checked = requireText(value, name);
        URI uri;
        try {
            uri = URI.create(checked);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(name + "必须是包含协议和主机的 Origin", exception);
        }
        String path = uri.getRawPath();
        boolean http = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
        if (!http
                || uri.getHost() == null
                || uri.getRawUserInfo() != null
                || (path != null && !path.isEmpty() && !"/".equals(path))
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException(name + "必须是包含协议和主机且不带路径的 Origin");
        }
        return checked.endsWith("/") ? checked.substring(0, checked.length() - 1) : checked;
    }

    private static @NotNull String requireText(@NotNull String value, @NotNull String name) {
        String checked = Objects.requireNonNull(value, name + "不能为空").trim();
        if (checked.isEmpty())
            throw new IllegalArgumentException(name + "不能为空");
        return checked;
    }

    private static @NotNull String requireCredential(@NotNull String value, @NotNull String name) {
        String checked = Objects.requireNonNull(value, name + "不能为空");
        if (checked.isEmpty() || checked.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException(name + "不能为空且不能包含控制字符");
        return checked;
    }
}
