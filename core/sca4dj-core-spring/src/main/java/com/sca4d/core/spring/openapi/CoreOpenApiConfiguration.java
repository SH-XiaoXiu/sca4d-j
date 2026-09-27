package com.sca4d.core.spring.openapi;

import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.spring.config.CoreSpringProperties;
import com.sca4d.core.spring.config.HttpInputValidationProperties;
import com.sca4d.core.spring.web.list.ListParameters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/** 注册脚手架的 OpenAPI 基础信息和 Bearer 认证方案。 */
@AutoConfiguration
@ConditionalOnClass({OpenAPI.class, SpringDocUtils.class})
@ConditionalOnProperty(prefix = "sca4dj.core", name = "open-api-enabled", havingValue = "true", matchIfMissing = true)
public class CoreOpenApiConfiguration {

    static {
        // HTTP JSON 把所有 Java 64 位整数写成字符串，OpenAPI 必须声明相同的传输类型。
        StringSchema longSchema = new StringSchema();
        longSchema.setDescription("64 位整数的十进制字符串");
        longSchema.setExample("9007199254740993");
        longSchema.addExtension(HttpInputOpenApiCustomizer.INTEGRAL, true);
        SpringDocUtils.getConfig()
                .replaceWithSchema(Long.class, longSchema)
                .replaceWithSchema(Long.TYPE, longSchema)
                .addJavaTypeToIgnore(PageRequest.class)
                .addJavaTypeToIgnore(ListParameters.class);
    }

    /** 创建可由应用覆盖的 OpenAPI 基础自动配置。 */
    public CoreOpenApiConfiguration() {
    }

    /**
     * 创建应用的 OpenAPI 基础定义。
     *
     * @param environment 应用名称配置；不使用中间件隔离前缀作为对外名称
     * @return 包含 Bearer 认证方案的 OpenAPI 定义
     */
    @Bean
    @ConditionalOnMissingBean
    public OpenAPI sca4djOpenApi(Environment environment) {
        return new OpenAPI()
                .info(
                        new Info().title(environment.getProperty("spring.application.name", "Sca4d-J") + " API")
                                .version("0.0.1-alpha"))
                .components(
                        new Components().addSecuritySchemes(
                                "bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("Opaque")));
    }

    /**
     * 创建将 HTTP 输入默认校验写入 OpenAPI 请求定义的定制器。
     *
     * @param properties HTTP 输入默认校验配置
     * @param coreProperties 分页和列表查询配置
     * @return 同时处理参数、属性、接口和最终文档的定制器
     */
    @Bean
    HttpInputOpenApiCustomizer httpInputOpenApiCustomizer(
            HttpInputValidationProperties properties,
            CoreSpringProperties coreProperties) {
        return new HttpInputOpenApiCustomizer(properties, coreProperties);
    }

}
