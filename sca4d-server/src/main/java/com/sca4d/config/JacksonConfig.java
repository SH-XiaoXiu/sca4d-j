package com.sca4d.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.sca4d.common.core.TimeSupport;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * Jackson 全局配置。
 */
@Configuration
public class JacksonConfig {

    /**
     * 注册项目共用 JSON 规则。
     *
     * @return Jackson 构建器定制器
     */
    @Bean
    public @NotNull Jackson2ObjectMapperBuilderCustomizer sca4dJacksonCustomizer() {
        return builder -> builder
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance)
                .timeZone(TimeZone.getTimeZone(TimeSupport.DEFAULT_ZONE));
    }
}
