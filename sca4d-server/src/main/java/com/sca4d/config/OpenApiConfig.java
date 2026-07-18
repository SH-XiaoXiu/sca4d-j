package com.sca4d.config;

import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import org.jetbrains.annotations.NotNull;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * OpenAPI 文档配置。
 */
@Configuration
public class OpenApiConfig {

    static {
        // 运行时 Jackson 已将 Long 序列化为字符串，文档必须保持同一契约，避免前端 ID 精度丢失。
        StringSchema idSchema = new StringSchema();
        idSchema.setDescription("64 位整数的十进制字符串");
        idSchema.setExample("9007199254740993");
        SpringDocUtils.getConfig()
                .replaceWithSchema(Long.class, idSchema)
                .replaceWithSchema(Long.TYPE, idSchema);
    }

    /**
     * 注册脚手架 OpenAPI 基础信息。
     *
     * @param buildProperties 当前构建生成的项目版本
     * @return 标题和发布版本与构建产物一致的 OpenAPI 基础信息
     */
    @Bean
    public @NotNull OpenAPI sca4dOpenApi(@NotNull BuildProperties buildProperties) {
        return new OpenAPI().info(new Info()
                .title("SCA4D API")
                .version(buildProperties.getVersion()));
    }

    /**
     * 同步项目运行时必填约束与 OpenAPI Schema。
     *
     * @return Required 字段转换器
     */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public @NotNull ModelConverter requiredModelConverter() {
        return new RequiredModelConverter();
    }
}
