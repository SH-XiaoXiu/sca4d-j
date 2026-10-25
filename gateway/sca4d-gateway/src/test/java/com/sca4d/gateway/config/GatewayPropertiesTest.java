package com.sca4d.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 验证 Gateway Secret、注册后端标识和时长配置。 */
class GatewayPropertiesTest {

    @Test
    @DisplayName("默认注册后端 service ID 可以直接使用")
    void acceptsDefaultBackendServiceId() {
        GatewayProperties properties = baseProperties();

        properties.validate();

        assertThat(properties.validatedBackendServiceId()).isEqualTo("sca4d-server");
    }

    @Test
    @DisplayName("注册后端 service ID 去除首尾空白")
    void normalizesBackendServiceId() {
        GatewayProperties properties = baseProperties();
        properties.setBackendServiceId(" sca4d-server ");

        assertThat(properties.validatedBackendServiceId()).isEqualTo("sca4d-server");
    }

    @Test
    @DisplayName("注册后端 service ID 拒绝 URI 和路径字符")
    void rejectsInvalidBackendServiceId() {
        GatewayProperties properties = baseProperties();
        properties.setBackendServiceId("lb://server");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("backend-service-id");
    }

    /** 创建包含有效 Gateway 控制配置的属性。 */
    private static GatewayProperties baseProperties() {
        GatewayProperties properties = new GatewayProperties();
        properties.setSecret("gateway-secret");
        return properties;
    }
}
