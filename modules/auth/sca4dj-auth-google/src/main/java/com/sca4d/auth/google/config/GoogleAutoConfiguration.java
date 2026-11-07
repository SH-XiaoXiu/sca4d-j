package com.sca4d.auth.google.config;

import com.sca4d.auth.federation.FederatedProviderDriver;
import com.sca4d.auth.google.GoogleProviderDriver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/** 登记 Google 资料转换 Driver；{@code sca4dj.auth.google.enabled=false} 时不登记。不注册应用 HTTP 路由。 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "sca4dj.auth.google", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GoogleAutoConfiguration {

    /** 创建 Google Driver，由 {@code FederatedProviderDriverRegistry} 收集。 */
    @Bean
    FederatedProviderDriver googleProviderDriver() {
        return new GoogleProviderDriver();
    }
}
