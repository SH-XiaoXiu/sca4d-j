package com.sca4d.open.config;

import com.sca4d.core.api.gateway.GatewayConstants;
import com.sca4d.core.api.setting.ConfiguredConstants;
import com.sca4d.core.spring.web.HttpConstants;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/** 在 Open 路由和认证 Bean 创建前固定公开路径与 HMAC Header。 */
public class OpenEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(
            @NotNull ConfigurableEnvironment environment,
            @NotNull SpringApplication application) {
        ConfiguredConstants.initialize(GatewayConstants.class, environment::getProperty);
        ConfiguredConstants.initialize(HttpConstants.class, environment::getProperty);
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
