package com.sca4d.core.spring.config;

import com.sca4d.core.api.isolation.AppName;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;
import java.util.UUID;

/** 在配置加载后、日志和 Bean 创建前固定进程标识，避免 random.uuid 被多次解析成不同值。 */
public class InstanceEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String SOURCE = "sca4djInstanceIdentity";

    @Override
    public void postProcessEnvironment(
            @NotNull ConfigurableEnvironment environment,
            @NotNull SpringApplication application) {
        AppName.publish(environment.getRequiredProperty("spring.application.name"));
        if (environment.getPropertySources().contains(SOURCE)) {
            return;
        }
        String configured = environment.getProperty("sca4dj.instance.id");
        String id = configured == null ? UUID.randomUUID().toString() : configured;
        if (!id.matches("[A-Za-z0-9._-]{1,128}")) {
            throw new IllegalStateException("sca4dj.instance.id 格式无效");
        }
        environment.getPropertySources().addFirst(new MapPropertySource(SOURCE, Map.of("sca4dj.instance.id", id)));
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
