package com.sca4d.openapi.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.setting.ConfiguredConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;

/** 验证独立 OpenAPI 路由模型对公开路径和稳定标识的约束。 */
class OpenApiRouteDefinitionTest {

    @BeforeAll
    static void initializeOpenApiConstants() {
        ConfiguredConstants.initialize(OpenApiConstants.class, ignored -> null);
    }

    @Test
    @DisplayName("保留合法路由定义的 HTTP 方法路径和 Operation")
    void keepsValidRouteDefinition() {
        OpenApiRouteDefinition definition = new OpenApiRouteDefinition(
                RequestMethod.GET,
                "/openapi/devices/{deviceId}",
                "iot",
                "getDevice",
                TreeCode.ofColon("iot:devices:read"));

        assertThat(definition.method()).isEqualTo(RequestMethod.GET);
        assertThat(definition.path()).isEqualTo("/openapi/devices/{deviceId}");
        assertThat(definition.toolGroup()).isEqualTo("iot");
        assertThat(definition.toolName()).isEqualTo("getDevice");
        assertThat(definition.openApiOperationId()).isEqualTo("iotGetDevice");
        assertThat(definition.operation()).isEqualTo(TreeCode.ofColon("IOT:DEVICES:READ"));
    }

    @Test
    @DisplayName("拒绝非 OpenAPI 路径和带查询参数的路由")
    void rejectsNonOpenApiPath() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> new OpenApiRouteDefinition(
                                RequestMethod.GET,
                                "/devices/{deviceId}",
                                "iot",
                                "getDevice",
                                TreeCode.ofColon("iot:devices:read")))
                .withMessageContaining("必须以 /openapi/");
    }
}
