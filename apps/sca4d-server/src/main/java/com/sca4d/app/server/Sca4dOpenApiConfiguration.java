package com.sca4d.app.server;

import com.sca4d.core.api.annotation.AnonymousAccess;
import com.sca4d.core.api.authentication.AuthTokenMode;
import com.sca4d.core.api.authentication.RequireSessionToken;
import com.sca4d.core.api.setting.value.SettingValues;
import com.sca4d.openapi.oauth.OpenApiAuthenticationEndpoint;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.util.List;

/** 使用完整示例自己的名称、版本和作者声明 OpenAPI 文档。 */
@Configuration(proxyBeanMethods = false)
public class Sca4dOpenApiConfiguration {

    /** 创建完整示例的 OpenAPI 配置。 */
    public Sca4dOpenApiConfiguration() {
    }

    /**
     * 选择示例 App 对第三方 Client 公开的 Client Credentials 认证路径。
     *
     * @return 由 OpenAPI 模块实现的认证端点配置
     */
    @Bean
    OpenApiAuthenticationEndpoint openApiAuthenticationEndpoint() {
        return OpenApiAuthenticationEndpoint.clientCredentials("/openapi/oauth/token");
    }

    /**
     * 创建示例 App 的 OpenAPI 定义。
     *
     * @param version 当前构建版本
     * @param settings 示例 App 产品设置
     * @return 示例 App OpenAPI 定义
     */
    @Bean
    OpenAPI sca4dOpenApi(
            @Value("${build.version:0.0.1-alpha}") String version,
            ProductSettings settings) {
        SettingValues product = new SettingValues(
                List.of(settings.DISPLAY_NAME.inspect(), settings.CONTACT_NAME.inspect()));
        return new OpenAPI()
                .info(
                        new Info()
                                .title(product.get(settings.DISPLAY_NAME) + " API")
                                .version(version)
                                .contact(new Contact().name(product.get(settings.CONTACT_NAME))))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "jwtBearer",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT"))
                                .addSecuritySchemes(
                                        "sessionBearer",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("Opaque")));
    }

    /**
     * 按默认 Token 模式与 {@link RequireSessionToken} 给每个非匿名 Operation 标记认证方案。
     *
     * @param tokenMode 普通请求默认使用的 Auth Token 模式
     * @return Springdoc Operation 自定义器
     */
    @Bean
    OperationCustomizer authTokenOperationCustomizer(
            @Value("${sca4dj.auth.token.mode:SESSION}") AuthTokenMode tokenMode) {
        return (operation, handlerMethod) -> {
            boolean anonymous = AnnotatedElementUtils.hasAnnotation(
                    handlerMethod.getMethod(),
                    AnonymousAccess.class)
                    || AnnotatedElementUtils.hasAnnotation(
                            handlerMethod.getBeanType(),
                            AnonymousAccess.class);
            if (anonymous) {
                operation.setSecurity(List.of());
                return operation;
            }
            boolean requireSession = AnnotatedElementUtils.hasAnnotation(
                    handlerMethod.getMethod(),
                    RequireSessionToken.class)
                    || AnnotatedElementUtils.hasAnnotation(
                            handlerMethod.getBeanType(),
                            RequireSessionToken.class);
            String scheme = requireSession || tokenMode == AuthTokenMode.SESSION
                    ? "sessionBearer"
                    : "jwtBearer";
            operation.setSecurity(List.of(new SecurityRequirement().addList(scheme)));
            return operation;
        };
    }
}
