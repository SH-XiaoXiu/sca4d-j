package com.sca4d.storage.minio;

import com.sca4d.core.api.storage.FileStorage;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** 根据 sca4dj.storage.minio 配置创建 MinIO 文件存储。 */
@AutoConfiguration
@EnableConfigurationProperties(MinioStorageProperties.class)
public class MinioStorageAutoConfiguration {
    /** 创建 MinIO 自动配置。 */
    public MinioStorageAutoConfiguration() {
    }

    @Bean
    @ConditionalOnProperty(prefix = "sca4dj.storage.minio", name = "enabled", havingValue = "true", matchIfMissing = true)
    FileStorage minioFileStorage(@NotNull MinioStorageProperties properties) {
        return new MinioFileStorage(properties);
    }
}
