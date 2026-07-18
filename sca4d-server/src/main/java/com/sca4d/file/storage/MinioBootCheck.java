package com.sca4d.file.storage;

import com.sca4d.common.boot.BootCheck;
import com.sca4d.common.boot.BootCheckPolicy;
import com.sca4d.common.boot.BootCheckResult;
import com.sca4d.common.boot.BootEnvironment;
import com.sca4d.config.properties.Sca4djFileProperties;
import com.sca4d.config.properties.Sca4djMinioProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.StringUtils;

/** MinIO 文件存储的配置与连通性启动检查。 */
public class MinioBootCheck implements BootCheck {

    private static final String DEFAULT_ACCESS_KEY = "minioadmin";
    private static final String DEFAULT_SECRET_KEY = "minioadmin";

    private final Sca4djFileProperties fileProperties;
    private final Sca4djMinioProperties minioProperties;
    private final MinioFileStorage storage;

    /**
     * 创建与当前存储选择和启动策略绑定的 MinIO 检查。
     *
     * @param fileProperties 文件服务开关和默认存储提供方
     * @param minioProperties MinIO 连接、凭据和启动检查策略
     * @param storage 待验证的 MinIO 存储实例
     */
    public MinioBootCheck(
            @NotNull Sca4djFileProperties fileProperties,
            @NotNull Sca4djMinioProperties minioProperties,
            @NotNull MinioFileStorage storage) {
        this.fileProperties = fileProperties;
        this.minioProperties = minioProperties;
        this.storage = storage;
    }

    @Override
    public @NotNull String name() {
        return "minio";
    }

    @Override
    public @NotNull BootCheckPolicy policy() {
        BootCheckPolicy policy = minioProperties.getStartupCheck();
        boolean enabled = fileProperties.isEnabled() && fileProperties.getStorage() == FileStorageProvider.MINIO;
        return new BootCheckPolicy(enabled && policy.isEnabled(), policy.getCriticality(), policy.getTimeout());
    }

    @Override
    public @NotNull BootCheckResult check(@NotNull BootEnvironment environment) {
        if (!StringUtils.hasText(minioProperties.getEndpoint())
                || !StringUtils.hasText(minioProperties.getPublicEndpoint())
                || !StringUtils.hasText(minioProperties.getAccessKey())
                || !StringUtils.hasText(minioProperties.getSecretKey())
                || !StringUtils.hasText(minioProperties.getBucket())) {
            return BootCheckResult.unavailable("MinIO 配置不完整");
        }
        if (environment.isProductionLike()
                && DEFAULT_ACCESS_KEY.equals(minioProperties.getAccessKey())
                && DEFAULT_SECRET_KEY.equals(minioProperties.getSecretKey())) {
            return BootCheckResult.unavailable("预发布和生产环境禁止使用 MinIO 默认凭据");
        }
        try {
            storage.verifyAvailable();
            return BootCheckResult.available("Bucket 已就绪");
        } catch (RuntimeException exception) {
            return BootCheckResult.unavailable("MinIO Bucket 不可用");
        }
    }
}
