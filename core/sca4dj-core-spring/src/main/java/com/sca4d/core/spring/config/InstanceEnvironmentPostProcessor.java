package com.sca4d.core.spring.config;

import com.sca4d.core.api.gateway.GatewayConstants;
import com.sca4d.core.api.isolation.AppName;
import com.sca4d.core.api.setting.ConfiguredConstants;
import com.sca4d.core.spring.web.HttpConstants;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 在配置加载后、日志和 Bean 创建前固定进程标识，避免 random.uuid 被多次解析成不同值。 */
public class InstanceEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String SOURCE = "sca4djInstanceIdentity";

    /**
     * 发布 `AppName`，校验网关协议常量和 HTTP 头常量后，把它们作为最高优先级属性源加入环境。
     *
     * <p>`sca4dj.instance.id` 未配置时生成一次随机 UUID；环境中已有实例标识属性源时不再生成，保证同一进程内的解析结果一致。
     * 配置的实例标识不符合 `[A-Za-z0-9._-]{1,128}` 时抛出 `IllegalStateException`，进程不启动。
     *
     * @param environment 正在加载的 Spring 环境，属性源会被修改
     * @param application 当前 Spring 应用
     * @throws IllegalStateException 实例标识格式无效时抛出
     */
    @Override
    public void postProcessEnvironment(
            @NotNull ConfigurableEnvironment environment,
            @NotNull SpringApplication application) {
        AppName.publish(environment.getRequiredProperty("spring.application.name"));
        Map<String, String> gatewayProtocol = ConfiguredConstants.initialize(
                GatewayConstants.class,
                environment::getProperty);
        GatewayConstants.validate();
        environment.getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "sca4djConfiguredGatewayProtocol",
                                new HashMap<String, Object>(gatewayProtocol)));
        Map<String, String> httpHeaders = ConfiguredConstants.initialize(HttpConstants.class, environment::getProperty);
        HttpConstants.validate();
        environment.getPropertySources()
                .addFirst(
                        new MapPropertySource("sca4djConfiguredHttpHeaders", new HashMap<String, Object>(httpHeaders)));
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
