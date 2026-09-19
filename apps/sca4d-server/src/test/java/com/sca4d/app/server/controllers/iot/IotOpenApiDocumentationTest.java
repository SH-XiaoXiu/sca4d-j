package com.sca4d.app.server.controllers.iot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.app.server.service.IotApplicationService;
import com.sca4d.openapi.web.OpenApiSpringdocAutoConfiguration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
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
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/** 验证示例应用 IoT 开放接口的运行时 schema、示例和管理端类型隔离。 */
@WebMvcTest(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
@ContextConfiguration(classes = IotOpenApiDocumentationTest.TestApplication.class)
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
class IotOpenApiDocumentationTest {

    private final MockMvc mvc;

    private final ObjectMapper objectMapper;

    @Autowired
    IotOpenApiDocumentationTest(MockMvc mvc, ObjectMapper objectMapper) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
    }

    @Test
    @DisplayName("IoT 开放接口生成独立类型和完整示例")
    void documentsIndependentDeviceContract() throws Exception {
        String content = mvc.perform(get("/v3/api-docs/openapi"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode document = objectMapper.readTree(content);

        JsonNode operation = document.path("paths")
                .path("/openapi/iot/devices/{deviceId}")
                .path("get");
        assertThat(operation.path("summary").textValue()).isEqualTo("查询 IoT 设备");
        assertThat(operation.path("tags").get(0).textValue()).isEqualTo("IoT 设备");
        assertThat(operation.path("operationId").textValue()).isEqualTo("iotGetDevice");
        assertThat(operation.path("parameters").get(0).path("schema").path("type").textValue())
                .isEqualTo("string");
        assertThat(operation.path("parameters").get(0).path("schema").path("example").isTextual()).isTrue();
        assertThat(
                operation.path("responses")
                        .path("200")
                        .path("content")
                        .path("application/json")
                        .path("examples")
                        .path("device")
                        .path("value")
                        .path("data")
                        .path("id")
                        .textValue())
                .isEqualTo("1057296481730560");
        assertThat(
                operation.path("responses")
                        .path("200")
                        .path("content")
                        .path("application/json")
                        .path("schema")
                        .path("$ref")
                        .textValue())
                .isEqualTo("#/components/schemas/ApiResponseIotDevice");
        assertThat(operation.path("responses").path("200").path("content").has("*/*")).isFalse();
        assertThat(
                operation.path("responses")
                        .path("404")
                        .path("content")
                        .path("application/json")
                        .path("examples")
                        .path("deviceNotFound")
                        .path("value")
                        .path("code")
                        .textValue())
                .isEqualTo("2901");

        JsonNode schemas = document.path("components").path("schemas");
        assertThat(schemas.has("IotDevice")).isTrue();
        assertThat(schemas.has("IotDeviceView")).isFalse();
        assertThat(schemas.path("IotDevice").path("properties").path("id").path("type").textValue())
                .isEqualTo("string");
        assertThat(schemas.path("IotDevice").path("properties").path("bindingGeneration").path("type").textValue())
                .isEqualTo("string");
        assertThat(schemas.path("IotDevice").path("properties").path("productSource").path("enum"))
                .containsExactly(
                        objectMapper.getNodeFactory().textNode("DATABASE"),
                        objectMapper.getNodeFactory().textNode("CODE"));
        assertThat(schemas.path("IotDevice").path("properties").path("status").path("enum"))
                .containsExactly(
                        objectMapper.getNodeFactory().textNode("ENABLED"),
                        objectMapper.getNodeFactory().textNode("DISABLED"));
    }

    /** 提供只装配真实 IoT 开放 Controller 和 Springdoc 的测试应用。 */
    @SpringBootConfiguration(proxyBeanMethods = false)
    @Import(OpenApiSpringdocAutoConfiguration.class)
    static class TestApplication {

        /** @return 测试使用的 OpenAPI 基础信息 */
        @Bean
        OpenAPI testOpenApi() {
            return new OpenAPI().info(new Info().title("Sca4d Server API").version("1"));
        }

        /** @return 测试读取文档时使用的 IoT 应用服务替身 */
        @Bean
        IotApplicationService iotApplicationService() {
            return mock(IotApplicationService.class);
        }

        /** @return 真实的 IoT 开放 API Controller */
        @Bean
        IotOpenApiController iotOpenApiController(IotApplicationService iot) {
            return new IotOpenApiController(iot);
        }
    }
}
