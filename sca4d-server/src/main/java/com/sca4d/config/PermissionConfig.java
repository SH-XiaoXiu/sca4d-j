package com.sca4d.config;

import com.sca4d.common.permission.PermissionCatalog;
import com.sca4d.common.permission.PermissionModule;
import com.sca4d.config.properties.Sca4djBootstrapProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 权限目录配置。
 */
@Configuration
@EnableConfigurationProperties(Sca4djBootstrapProperties.class)
public class PermissionConfig {

    /**
     * 注册权限目录。
     *
     * @param modules 权限模块集合
     * @return 权限目录
     */
    @Bean
    @ConditionalOnMissingBean(PermissionCatalog.class)
    public @NotNull PermissionCatalog permissionCatalog(@NotNull List<PermissionModule> modules) {
        return new PermissionCatalog(modules);
    }
}
