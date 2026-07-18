package com.sca4d.config;

import com.sca4d.common.exception.ErrorCodeCatalog;
import com.sca4d.common.exception.ErrorCodeScanner;
import com.sca4d.config.properties.Sca4djErrorCodeProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 错误码配置。
 */
@Configuration
@EnableConfigurationProperties(Sca4djErrorCodeProperties.class)
public class ErrorCodeConfig {

    /**
     * 注册错误码扫描器。
     *
     * @return 错误码扫描器
     */
    @Bean
    public @NotNull ErrorCodeScanner errorCodeScanner() {
        return new ErrorCodeScanner();
    }

    /**
     * 注册错误码目录。
     *
     * @param scanner 错误码扫描器
     * @param properties 错误码配置
     * @param applicationContext Spring 应用上下文
     * @return 错误码目录
     */
    @Bean
    public @NotNull ErrorCodeCatalog errorCodeCatalog(
            @NotNull ErrorCodeScanner scanner,
            @NotNull Sca4djErrorCodeProperties properties,
            @NotNull ApplicationContext applicationContext) {
        Set<String> basePackages = new LinkedHashSet<>(properties.getBasePackages());
        if (basePackages.isEmpty()) {
            // 未显式配置时，使用 Spring Boot 自动配置包，避免业务模块忘记注册错误码。
            List<String> packages = AutoConfigurationPackages.get(applicationContext);
            basePackages.addAll(packages);
        }
        ErrorCodeCatalog.Builder catalog = ErrorCodeCatalog.builder();
        scanner.scan(basePackages).forEach(catalog::register);
        return catalog.build();
    }
}
