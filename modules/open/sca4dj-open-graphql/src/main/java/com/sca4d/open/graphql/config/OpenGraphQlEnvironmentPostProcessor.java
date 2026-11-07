package com.sca4d.open.graphql.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * 默认让 Spring GraphQL 从所有 classpath 根读取 Schema 文件。
 *
 * <p>Spring GraphQL 默认的 {@code classpath:} 位置只使用第一个包含 {@code graphql/} 目录的 classpath 根，
 * 具体应用自带 Schema 时，本模块的 {@code @openOperation} 指令声明不会被加载。本类把
 * {@code spring.graphql.schema.locations} 的默认值改为 {@code classpath*:} 位置；应用显式配置该键时以应用配置为准。
 */
public class OpenGraphQlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String SCHEMA_LOCATIONS = "spring.graphql.schema.locations";

    @Override
    public void postProcessEnvironment(
            @NotNull ConfigurableEnvironment environment,
            @NotNull SpringApplication application) {
        environment.getPropertySources()
                .addLast(
                        new MapPropertySource(
                                "sca4djOpenGraphQlDefaults",
                                Map.of(SCHEMA_LOCATIONS, "classpath*:graphql/**/")));
    }
}
