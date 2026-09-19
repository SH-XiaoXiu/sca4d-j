package com.sca4d.gateway.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** 注册经过完整校验的 Gateway 运行配置。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayConfiguration {

    /**
     * 校验 Gateway 配置并保存规范化的 Secret。
     *
     * @param properties Gateway 运行配置
     * @throws IllegalStateException Secret 或时长配置不合法时抛出
     */
    public GatewayConfiguration(@NotNull GatewayProperties properties) {
        String secret = properties.requireSecret();
        properties.setSecret(secret);
        properties.validate();
    }

    /** @return Gateway 租约过期判断使用的 UTC 时钟 */
    @Bean
    @NotNull
    Clock gatewayClock() {
        return Clock.systemUTC();
    }
}
