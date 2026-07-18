package com.sca4d.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.sca4d.common.web.view.BaseViewSerializerModifier;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * BaseView Jackson 配置。
 */
@Configuration
public class BaseViewJacksonConfig {

    /**
     * 注册 BaseView 序列化扩展。
     *
     * @return Jackson 构建器定制器
     */
    @Bean
    public @NotNull Jackson2ObjectMapperBuilderCustomizer baseViewJacksonCustomizer() {
        return builder -> {
            // 创建独立模块，避免污染其他 Jackson 配置职责。
            SimpleModule module = new SimpleModule("sca4d-base-view");
            // 通过 BeanSerializerModifier 包装 BaseView 默认序列化结果。
            module.setSerializerModifier(new BaseViewSerializerModifier());
            // 把模块交给 Spring Boot 的 ObjectMapper 构建操作。
            builder.modulesToInstall(module);
        };
    }
}
