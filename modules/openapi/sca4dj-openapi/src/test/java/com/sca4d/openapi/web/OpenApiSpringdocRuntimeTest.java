package com.sca4d.openapi.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.auth.session.SessionService;
import com.sca4d.auth.sharedsecret.SharedSecretAuthenticationMethod;
import com.sca4d.iam.identity.IdentityService;
import com.sca4d.openapi.client.OpenApiClientService;
import com.sca4d.openapi.oauth.OpenApiAuthenticationEndpoint;
import com.sca4d.openapi.oauth.OpenApiJwtTokenService;
import com.sca4d.openapi.oauth.OpenApiTokenEndpointHandler;
import com.sca4d.openapi.web.annotation.OpenApiRoute;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.configuration.MultipleOpenApiSupportConfiguration;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** 验证 Springdoc 运行时生成的“开放 API”分组和动态认证端点契约。 */
@WebMvcTest(controllers = OpenApiSpringdocRuntimeTest.InternalController.class, properties = {
        "spring.application.name=openapi-test",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
@ContextConfiguration(classes = OpenApiSpringdocRuntimeTest.TestApplication.class)
@ImportAutoConfiguration({
        SpringDocConfiguration.class,
        SpringDocConfigProperties.class,
        SpringDocWebMvcConfiguration.class,
        MultipleOpenApiSupportConfiguration.class,
        SwaggerConfig.class,
        SwaggerUiConfigProperties.class,
        SwaggerUiOAuthProperties.class
})
@AutoConfigureMockMvc
class OpenApiSpringdocRuntimeTest {

    private final MockMvc mvc;

    private final ObjectMapper objectMapper;

    @Autowired
    OpenApiSpringdocRuntimeTest(MockMvc mvc, ObjectMapper objectMapper) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
    }

    @Test
    @DisplayName("Swagger 配置使用开放 API 显示名")
    void exposesOpenApiGroupInSwaggerConfiguration() throws Exception {
        JsonNode configuration = read("/v3/api-docs/swagger-config");

        assertThat(configuration.path("urls"))
                .anySatisfy(group -> {
                    assertThat(group.path("name").textValue()).isEqualTo("开放 API");
                    assertThat(group.path("url").textValue()).isEqualTo("/v3/api-docs/openapi");
                });
    }

    @Test
    @DisplayName("开放 API 文档只包含认证端点和显式声明的方法")
    void documentsConfiguredAuthenticationAndDeclaredTools() throws Exception {
        JsonNode document = read("/v3/api-docs/openapi");
        JsonNode paths = document.path("paths");

        assertThat(paths.fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder("/partner/oauth/token", "/openapi/devices/{deviceId}");
        assertThat(paths.has("/internal/health")).isFalse();

        JsonNode token = paths.path("/partner/oauth/token").path("post");
        assertThat(token.path("tags").get(0).textValue()).isEqualTo("认证");
        assertThat(token.path("security").get(0).has("openApiClientBasic")).isTrue();
        assertThat(token.path("requestBody").path("content").has(MediaType.APPLICATION_FORM_URLENCODED_VALUE))
                .isTrue();
        assertThat(token.path("responses").fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder("200", "400", "401");
        assertThat(
                token.path("responses")
                        .path("400")
                        .path("content")
                        .path("application/json")
                        .path("schema")
                        .path("$ref")
                        .textValue())
                .isEqualTo("#/components/schemas/OpenApiError");

        JsonNode device = paths.path("/openapi/devices/{deviceId}").path("get");
        assertThat(device.path("operationId").textValue()).isEqualTo("iotGetDevice");
        assertThat(device.path("tags").get(0).textValue()).isEqualTo("IoT 设备");
        assertThat(device.path("x-tool-group").textValue()).isEqualTo("iot");
        assertThat(device.path("x-tool-name").textValue()).isEqualTo("getDevice");
        assertThat(device.path("security")).hasSize(2);
        assertThat(device.path("responses").has("400")).isTrue();
        assertThat(device.path("responses").has("401")).isTrue();
        assertThat(device.path("responses").has("403")).isTrue();
        assertThat(device.path("responses").has("500")).isTrue();

        JsonNode schemes = document.path("components").path("securitySchemes");
        assertThat(schemes.fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder("openApiBearer", "openApiHmac", "openApiClientBasic");
        assertThat(document.path("components").path("schemas").has("OpenApiError")).isTrue();
    }

    /** 读取指定 Springdoc 运行时端点并解析 JSON 文档。 */
    private @NotNull JsonNode read(@NotNull String path) throws Exception {
        String content = mvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(content);
    }

    /** 提供不依赖 OpenAPI 业务模块完整装配的最小 Springdoc 测试应用。 */
    @SpringBootConfiguration(proxyBeanMethods = false)
    @Import(OpenApiSpringdocAutoConfiguration.class)
    static class TestApplication {

        /** @return 测试文档的基础名称和版本 */
        @Bean
        OpenAPI testOpenApi() {
            return new OpenAPI().info(new Info().title("测试 API").version("1"));
        }

        /** @return 由 Spring MVC 直接注册的测试 Tool Handler */
        @Bean
        TestToolHandler testToolHandler() {
            return new TestToolHandler();
        }

        /** @return 应当被开放 API 分组排除的普通管理 Controller */
        @Bean
        InternalController internalController() {
            return new InternalController();
        }

        /** @return 只用于 Springdoc 读取方法注解的 Token Handler */
        @Bean
        OpenApiTokenEndpointHandler openApiTokenEndpointHandler() {
            return new OpenApiTokenEndpointHandler(
                    mock(OpenApiClientService.class),
                    mock(IdentityService.class),
                    mock(SharedSecretAuthenticationMethod.class),
                    mock(SessionService.class),
                    mock(OpenApiJwtTokenService.class));
        }

        /** @return 测试应用选择的认证端点路径 */
        @Bean
        OpenApiAuthenticationEndpoint openApiAuthenticationEndpoint() {
            return OpenApiAuthenticationEndpoint.clientCredentials("/partner/oauth/token");
        }

        /** @return 使用非 {@code /openapi/**} 路径的认证端点注册器 */
        @Bean
        OpenApiAuthenticationEndpointRegistrar authenticationEndpointRegistrar(
                @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings,
                OpenApiAuthenticationEndpoint endpoint,
                OpenApiTokenEndpointHandler handler) {
            return new OpenApiAuthenticationEndpointRegistrar(
                    mappings,
                    endpoint,
                    handler);
        }

    }

    /** 模拟具体应用显式声明并补充 Swagger 说明的 Tool Handler。 */
    @RestController
    @Tag(name = "IoT 设备", description = "读取 IoT 设备。")
    static class TestToolHandler {

        /** @return 测试设备响应 */
        @OpenApiRoute(method = RequestMethod.GET, path = "/openapi/devices/{deviceId}", toolGroup = "iot", toolName = "getDevice", operation = "iot:devices:read")
        @Operation(summary = "查询 IoT 设备")
        public @NotNull TestDeviceResponse getDevice(@PathVariable @NotNull String deviceId) {
            return new TestDeviceResponse(deviceId);
        }
    }

    /** 模拟不能出现在开放 API 分组中的普通管理 Controller。 */
    @RestController
    static class InternalController {

        /** @return 固定健康状态 */
        @GetMapping("/internal/health")
        public @NotNull String health() {
            return "UP";
        }
    }

    /** 模拟具体应用为开放 API 单独声明的响应模型。 */
    @Schema(name = "TestDevice")
    static class TestDeviceResponse {

        private final @NotNull String id;

        TestDeviceResponse(@NotNull String id) {
            this.id = id;
        }

        /** @return Device 标识 */
        public @NotNull String getId() {
            return id;
        }
    }
}
