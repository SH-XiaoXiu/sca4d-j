package com.sca4d.auth.apple.config;

import com.sca4d.auth.apple.AppleClientSecretGenerator;
import com.sca4d.auth.apple.AppleProviderDriver;
import com.sca4d.auth.federation.FederatedProviderDriver;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/** 登记 Apple 资料转换 Driver；{@code sca4dj.auth.apple.enabled=false} 时不登记。不注册应用 HTTP 路由。 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "sca4dj.auth.apple", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AppleAutoConfiguration {

    /** 创建 Apple Driver，由 {@code FederatedProviderDriverRegistry} 收集。 */
    @Bean
    FederatedProviderDriver appleProviderDriver(@NotNull Clock clock) {
        return new AppleProviderDriver(new AppleClientSecretGenerator(clock));
    }
}
