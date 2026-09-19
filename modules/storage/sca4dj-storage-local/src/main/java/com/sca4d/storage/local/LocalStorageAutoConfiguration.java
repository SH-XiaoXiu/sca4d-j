package com.sca4d.storage.local;

import com.sca4d.core.api.storage.FileStorage;
import com.sca4d.core.api.storage.StorageTransferSigner;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** 自动装配使用 App 签名传输路由的本地目录存储。 */
@AutoConfiguration
@EnableConfigurationProperties(LocalStorageProperties.class)
public class LocalStorageAutoConfiguration {
    /** 创建本地存储自动配置。 */
    public LocalStorageAutoConfiguration() {
    }

    @Bean
    @ConditionalOnProperty(prefix = "sca4dj.storage.local", name = "enabled", havingValue = "true", matchIfMissing = true)
    FileStorage localFileStorage(
            @NotNull LocalStorageProperties properties,
            @NotNull StorageTransferSigner signer) {
        return new LocalFileStorage(properties, signer);
    }
}
