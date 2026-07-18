package com.sca4d.config;

import com.sca4d.file.FileService;
import com.sca4d.file.FileServiceUnavailable;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 文件模块关闭时的依赖占位配置。 */
@Configuration
@ConditionalOnProperty(prefix = "sca4dj.file", name = "enabled", havingValue = "false")
public class FileFallbackConfig {

    /**
     * 注册明确拒绝文件动作的占位服务。
     *
     * @return 文件模块关闭实现
     */
    @Bean
    @ConditionalOnMissingBean(FileService.class)
    public @NotNull FileService fileServiceUnavailable() {
        return new FileServiceUnavailable();
    }
}
