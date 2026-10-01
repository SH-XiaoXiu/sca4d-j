package com.sca4d.core.spring.setting;

import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 配置系统设置初始化使用的环境变量前缀；未配置时由应用名称推导。 */
@ConfigurationProperties("sca4dj.environment")
public final class EnvironmentPrefixProperties {

    private @Nullable String prefix;

    /** @return 显式环境变量前缀；为空时使用应用名称 */
    public @Nullable String getPrefix() {
        return prefix;
    }

    /** @param prefix 不包含末尾下划线的环境变量前缀 */
    public void setPrefix(@Nullable String prefix) {
        this.prefix = prefix;
    }
}
