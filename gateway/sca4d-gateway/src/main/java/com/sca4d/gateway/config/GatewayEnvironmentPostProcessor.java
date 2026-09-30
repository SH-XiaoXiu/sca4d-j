package com.sca4d.gateway.config;

import com.sca4d.core.api.gateway.GatewayConstants;
import com.sca4d.core.api.setting.ConfiguredConstants;
import com.sca4d.gateway.routing.GatewayRoutePaths;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/** 在 Gateway Bean 和路由配置加载前发布控制 Header 与路径常量。 */
public class GatewayEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(
            @NotNull ConfigurableEnvironment environment,
            @NotNull SpringApplication application) {
        Map<String, String> values = ConfiguredConstants.initialize(GatewayConstants.class, environment::getProperty);
        GatewayConstants.validate();
        environment.getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "sca4djConfiguredGatewayProtocol",
                                new HashMap<String, Object>(values)));
        Map<String, String> routes = ConfiguredConstants.initialize(GatewayRoutePaths.class, environment::getProperty);
        GatewayRoutePaths.validate();
        environment.getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "sca4djConfiguredGatewayRoutes",
                                new HashMap<String, Object>(routes)));
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
