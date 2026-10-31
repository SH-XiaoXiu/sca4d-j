package com.sca4d.openapi.web;

import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.sca4d.open.oauth.OpenAuthenticationEndpoint;
import com.sca4d.open.oauth.OpenTokenEndpointHandler;
import com.sca4d.open.web.OpenAuthenticationEndpointRegistrar;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 为 Springdoc 生成 OpenAPI Tool 路由、认证方案、错误响应和独立文档分组。 */
@AutoConfiguration(after = OpenApiAutoConfiguration.class)
@ConditionalOnClass({OpenAPI.class, OperationCustomizer.class, GroupedOpenApi.class})
public class OpenApiSpringdocAutoConfiguration {

    private static final String ERROR_SCHEMA = "#/components/schemas/OpenApiError";

    private static final Set<String> OPEN_API_SECURITY_SCHEMES = Set.of(
            "openApiBearer",
            "openApiHmac",
            "openApiClientBasic");

    /** 创建 OpenAPI Tool 使用的认证方案和固定错误结构。 */
    @Bean
    GlobalOpenApiCustomizer openApiComponentsCustomizer() {
        return openApi -> {
            Components components = openApi.getComponents();
            if (components == null) {
                components = new Components();
                openApi.setComponents(components);
            }
            components.addSecuritySchemes(
                    "openApiBearer",
                    new SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("OpenAPI access token")
                            .description("OAuth client_credentials 签发的 OpenAPI Bearer Token。"));
            components.addSecuritySchemes(
                    "openApiHmac",
                    new SecurityScheme()
                            .type(SecurityScheme.Type.APIKEY)
                            .in(SecurityScheme.In.HEADER)
                            .name(OpenApiConstants.SIGNATURE_HEADER)
                            .description(
                                    "OpenAPI HMAC 请求签名；同时提交 " + OpenApiConstants.CLIENT_ID_HEADER + "、"
                                            + OpenApiConstants.TIMESTAMP_HEADER + " 和 "
                                            + OpenApiConstants.NONCE_HEADER + "。"));
            components.addSecuritySchemes(
                    "openApiClientBasic",
                    new SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("basic")
                            .description("Open Client ID 和 Shared Secret。"));
            components.addSchemas("OpenApiError", errorSchema());
        };
    }

    /**
     * 把 Tool 分组和名称写入标准 OpenAPI 字段，并补充所有 Tool 共用的固定失败响应。
     *
     * @return 最后执行的 Tool Operation 定制器，避免具体应用的普通认证方案覆盖 Tool 认证方案
     */
    @Bean
    @Order
    OperationCustomizer openApiOperationCustomizer(OpenApiRouteCatalog routes) {
        return (operation, handlerMethod) -> {
            OpenApiRouteDefinition route = routes.find(handlerMethod).orElse(null);
            if (route != null) {
                operation.setOperationId(route.openApiOperationId());
                if (operation.getTags() == null || operation.getTags().isEmpty()) {
                    operation.setTags(List.of(route.toolGroup()));
                }
                operation.addExtension("x-tool-group", route.toolGroup());
                operation.addExtension("x-tool-name", route.toolName());
                operation.setSecurity(
                        List.of(
                                new SecurityRequirement().addList("openApiBearer"),
                                new SecurityRequirement().addList("openApiHmac")));
                addCommonToolResponses(operation);
            } else if (OpenTokenEndpointHandler.class.isAssignableFrom(handlerMethod.getBeanType())) {
                operation.setSecurity(List.of(new SecurityRequirement().addList("openApiClientBasic")));
            }
            return operation;
        };
    }

    /** 创建只包含认证端点和已登记 Tool 路由的 Springdoc 分组。 */
    @Bean
    GroupedOpenApi openApiGroup(
            ObjectProvider<OpenAuthenticationEndpointRegistrar> authenticationEndpointRegistrar,
            ObjectProvider<OpenAuthenticationEndpoint> authenticationEndpoint,
            OpenApiRouteCatalog routes,
            @Qualifier("openApiOperationCustomizer") OperationCustomizer operationCustomizer) {
        authenticationEndpointRegistrar.getIfAvailable();
        OpenAuthenticationEndpoint endpoint = authenticationEndpoint.getIfAvailable();
        return GroupedOpenApi.builder()
                .group("openapi")
                .displayName("开放 API")
                .addOpenApiMethodFilter(routes::contains)
                .addOperationCustomizer(operationCustomizer)
                .addOpenApiCustomizer(openApi -> {
                    if (openApi.getInfo() != null) {
                        openApi.getInfo().setTitle(openApi.getInfo().getTitle() + " 开放 API");
                        openApi.getInfo().setDescription("供第三方程序完成认证并调用具体应用明确公开的业务接口。");
                    }
                    openApi.addTagsItem(
                            new Tag()
                                    .name("认证")
                                    .description("使用 Open Client 凭据交换短期 Access Token。"));
                    Components components = openApi.getComponents();
                    if (components == null) {
                        components = new Components();
                        openApi.setComponents(components);
                    }
                    if (components.getSecuritySchemes() != null) {
                        components.getSecuritySchemes().keySet().retainAll(OPEN_API_SECURITY_SCHEMES);
                    }
                    if (endpoint != null) {
                        components.addSchemas("OpenApiToken", tokenSchema());
                        openApi.path(endpoint.path(), tokenPath());
                    }
                })
                .build();
    }

    /** 创建具体应用选择的 Client Credentials 认证路径的 POST 文档。 */
    private static PathItem tokenPath() {
        ObjectSchema form = new ObjectSchema();
        form.setRequired(List.of("grant_type"));
        StringSchema grantType = new StringSchema();
        grantType.setDescription("OAuth 2.0 grant type，固定为 client_credentials。");
        grantType.addEnumItem("client_credentials");
        grantType.setExample("client_credentials");
        form.addProperty("grant_type", grantType);

        Operation operation = new Operation()
                .operationId("openApiClientCredentialsToken")
                .addTagsItem("认证")
                .summary("签发 OpenAPI Access Token")
                .description("使用 HTTP Basic 中的 Client ID 和 Shared Secret 完成 Client Credentials 认证并签发 Bearer Token。")
                .security(List.of(new SecurityRequirement().addList("openApiClientBasic")))
                .requestBody(
                        new RequestBody()
                                .required(true)
                                .content(
                                        new Content().addMediaType(
                                                APPLICATION_FORM_URLENCODED_VALUE,
                                                new MediaType().schema(form))))
                .responses(
                        new ApiResponses()
                                .addApiResponse(
                                        "200",
                                        new ApiResponse()
                                                .description("认证成功并签发 Access Token")
                                                .content(
                                                        new Content().addMediaType(
                                                                APPLICATION_JSON_VALUE,
                                                                new MediaType()
                                                                        .schema(
                                                                                new Schema<>().$ref(
                                                                                        "#/components/schemas/OpenApiToken"))
                                                                        .addExamples(
                                                                                "token",
                                                                                new Example()
                                                                                        .summary("Token 签发成功")
                                                                                        .value(tokenExample())))))
                                .addApiResponse(
                                        "400",
                                        errorResponse(
                                                "grant_type 缺失或表单格式不合法",
                                                "1100",
                                                "请求参数不合法"))
                                .addApiResponse(
                                        "401",
                                        errorResponse(
                                                "Client ID、Shared Secret、Client 状态或 Account 状态不允许签发 Token",
                                                "2100",
                                                "认证失败")));
        return new PathItem().post(operation);
    }

    /** 创建 Client Credentials 成功响应的固定 Schema。 */
    private static Schema<Object> tokenSchema() {
        ObjectSchema schema = new ObjectSchema();
        schema.setDescription("Open Client Credentials 签发的短期 Bearer Token。");
        schema.setRequired(List.of("access_token", "expires_in", "scope", "token_type"));
        schema.addProperty(
                "access_token",
                new StringSchema()
                        .description("请求开放 API 使用的 Bearer Token。")
                        .example("eyJraWQiOiJvcGVuYXBpLTIwMjYtMDgifQ.example"));
        schema.addProperty(
                "expires_in",
                new IntegerSchema()
                        .description("Token 剩余有效秒数。")
                        .minimum(BigDecimal.ONE)
                        .example(300));
        schema.addProperty(
                "scope",
                new StringSchema()
                        .description("空格分隔的授权 Operation。")
                        .example("iot:devices:read"));
        StringSchema tokenType = new StringSchema();
        tokenType.setDescription("固定为 Bearer。");
        tokenType.addEnumItem("Bearer");
        tokenType.setExample("Bearer");
        schema.addProperty("token_type", tokenType);
        return schema;
    }

    /** 创建不包含真实凭据的 Token 成功示例。 */
    private static Map<String, Object> tokenExample() {
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("access_token", "eyJraWQiOiJvcGVuYXBpLTIwMjYtMDgifQ.example");
        example.put("expires_in", 300);
        example.put("scope", "iot:devices:read");
        example.put("token_type", "Bearer");
        return example;
    }

    /** 创建所有开放 API 失败响应共用的错误 schema。 */
    private static Schema<Object> errorSchema() {
        ObjectSchema schema = new ObjectSchema();
        schema.setDescription("OpenAPI 请求失败时返回的固定错误结构。");
        schema.setRequired(List.of("code", "messageArgs", "timestamp"));
        schema.addProperty("code", new StringSchema().description("稳定业务错误码。").example("1002"));
        schema.addProperty("messageKey", new StringSchema().description("稳定消息键。").nullable(true));
        Schema<List<Object>> messageArguments = new Schema<>();
        messageArguments.setType("array");
        messageArguments.setDescription("消息模板参数。");
        messageArguments.setItems(new ObjectSchema());
        schema.addProperty("messageArgs", messageArguments);
        schema.addProperty("message", new StringSchema().description("当前语言的安全展示文案。").nullable(true));
        schema.addProperty("data", new ObjectSchema().description("可安全返回的错误详情。").nullable(true));
        schema.addProperty("callId", new StringSchema().description("请求追踪编号。").nullable(true));
        schema.addProperty("timestamp", new DateTimeSchema().description("错误响应生成时间。"));
        return schema;
    }

    /** 为开放业务方法补充认证、授权、参数和服务端固定失败响应。 */
    private static void addCommonToolResponses(Operation operation) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }
        responses.putIfAbsent("400", errorResponse("请求参数不合法", "1100", "请求参数不合法"));
        responses.putIfAbsent("401", errorResponse("OpenAPI 认证失败", "1001", "需要完成认证"));
        responses.putIfAbsent("403", errorResponse("Client 或当前主体没有目标权限", "1002", "没有操作权限"));
        responses.putIfAbsent("500", errorResponse("服务端无法完成请求", "1099", "服务暂时无法完成请求"));
    }

    /** 创建引用 {@code OpenApiError} 并包含指定示例的错误响应。 */
    private static ApiResponse errorResponse(
            String description,
            String code,
            String message) {
        return new ApiResponse()
                .description(description)
                .content(
                        new Content().addMediaType(
                                APPLICATION_JSON_VALUE,
                                new MediaType()
                                        .schema(new Schema<>().$ref(ERROR_SCHEMA))
                                        .addExamples("error", new Example().value(errorExample(code, message)))));
    }

    /** 创建不包含敏感信息的统一错误响应示例。 */
    private static Map<String, Object> errorExample(String code, String message) {
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("code", code);
        example.put("messageKey", null);
        example.put("messageArgs", List.of());
        example.put("message", message);
        example.put("data", null);
        example.put("callId", "01K2N8Y6T4FJ7N8Z5P3Q6R9S0A");
        example.put("timestamp", "2026-08-15T02:30:00Z");
        return example;
    }
}
