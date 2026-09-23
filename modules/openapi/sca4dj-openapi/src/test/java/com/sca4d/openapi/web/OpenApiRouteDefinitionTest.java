package com.sca4d.openapi.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.sca4d.core.api.code.TreeCode;
import com.sca4d.openapi.web.annotation.OpenApiRoute;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.Method;

/** 验证 OpenAPI 路由定义对公开路径和稳定标识的约束。 */
class OpenApiRouteDefinitionTest {

    @Test
    @DisplayName("保留合法路由声明的 HTTP 方法路径和 Operation")
    void keepsValidRouteDeclaration() throws NoSuchMethodException {
        Method method = Routes.class.getDeclaredMethod("valid", String.class);

        OpenApiRouteDefinition definition = new OpenApiRouteDefinition(
                method.getAnnotation(OpenApiRoute.class));

        assertThat(definition.method()).isEqualTo(RequestMethod.GET);
        assertThat(definition.path()).isEqualTo("/openapi/devices/{deviceId}");
        assertThat(definition.toolGroup()).isEqualTo("iot");
        assertThat(definition.toolName()).isEqualTo("getDevice");
        assertThat(definition.openApiOperationId()).isEqualTo("iotGetDevice");
        assertThat(definition.operation()).isEqualTo(TreeCode.ofColon("IOT:DEVICES:READ"));
    }

    @Test
    @DisplayName("拒绝非 OpenAPI 路径和带查询参数的路由")
    void rejectsNonOpenApiPath() throws NoSuchMethodException {
        Method method = Routes.class.getDeclaredMethod("invalid", String.class);

        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> new OpenApiRouteDefinition(
                                method.getAnnotation(OpenApiRoute.class)))
                .withMessageContaining("必须以 /openapi/");
    }

    /** 测试使用的路由声明容器。 */
    static class Routes {

        @OpenApiRoute(method = RequestMethod.GET, path = "/openapi/devices/{deviceId}", toolGroup = "iot", toolName = "getDevice", operation = "iot:devices:read")
        String valid(@PathVariable String deviceId) {
            return deviceId;
        }

        @OpenApiRoute(method = RequestMethod.GET, path = "/devices/{deviceId}", toolGroup = "iot", toolName = "getDevice", operation = "iot:devices:read")
        String invalid(@PathVariable String deviceId) {
            return deviceId;
        }
    }
}
