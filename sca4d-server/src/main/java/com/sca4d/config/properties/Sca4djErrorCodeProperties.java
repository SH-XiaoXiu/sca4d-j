package com.sca4d.config.properties;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 错误码配置。
 */
@ConfigurationProperties(prefix = "sca4dj.error-code")
public class Sca4djErrorCodeProperties {

    private Set<String> basePackages = new LinkedHashSet<>();

    /**
     * 获取错误码扫描基础包。
     *
     * @return 基础包集合
     */
    public @NotNull Set<String> getBasePackages() {
        return basePackages;
    }

    /**
     * 设置错误码扫描基础包。
     *
     * @param basePackages 基础包集合
     */
    public void setBasePackages(@NotNull Set<String> basePackages) {
        this.basePackages = new LinkedHashSet<>(basePackages);
    }
}
