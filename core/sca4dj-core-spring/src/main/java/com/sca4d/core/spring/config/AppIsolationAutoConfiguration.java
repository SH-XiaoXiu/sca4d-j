package com.sca4d.core.spring.config;

import com.sca4d.core.api.isolation.AppName;
import com.sca4d.core.api.name.Name;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/** 在内部设施创建前校验并发布全局 App 名称。 */
@AutoConfiguration(before = {CoreRedisAutoConfiguration.class, CoreJacksonAutoConfiguration.class})
public class AppIsolationAutoConfiguration {

    /** 发布配置中的 App 名；缺失、非法或与进程已发布名称不同时拒绝启动。 */
    @Bean(name = "sca4djAppName", defaultCandidate = false)
    public @NotNull Name appName(@NotNull Environment environment) {
        AppName.publish(environment.getRequiredProperty("spring.application.name"));
        return AppName.current();
    }
}
