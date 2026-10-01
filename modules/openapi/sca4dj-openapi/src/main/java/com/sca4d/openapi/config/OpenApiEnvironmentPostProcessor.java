package com.sca4d.openapi.config;

import com.sca4d.core.api.gateway.GatewayConstants;
import com.sca4d.core.api.setting.ConfiguredConstants;
import com.sca4d.core.spring.web.HttpConstants;
import com.sca4d.openapi.web.OpenApiConstants;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/** 在 OpenAPI 路由和认证 Bean 创建前固定公开路径与 HMAC Header。 */
public class OpenApiEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(
            @NotNull ConfigurableEnvironment environment,
            @NotNull SpringApplication application) {
        ConfiguredConstants.initialize(GatewayConstants.class, environment::getProperty);
        ConfiguredConstants.initialize(HttpConstants.class, environment::getProperty);
        Map<String, String> values = ConfiguredConstants.initialize(OpenApiConstants.class, environment::getProperty);
        OpenApiConstants.validate();
        environment.getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "sca4djConfiguredOpenApi",
                                new HashMap<String, Object>(values)));
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
