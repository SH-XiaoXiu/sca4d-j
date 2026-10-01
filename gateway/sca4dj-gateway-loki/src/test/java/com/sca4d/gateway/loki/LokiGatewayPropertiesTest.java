package com.sca4d.gateway.loki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** 验证 Loki addon 的目标、路径和凭据限制。 */
class LokiGatewayPropertiesTest {

    @Test
    @DisplayName("启用配置接受 HTTP Origin 和可配置写入路径")
    void validatesOriginPathsAndCredentials() {
        LokiGatewayProperties properties = properties();
        properties.setLokiOrigin("http://localhost:3100/");
        properties.setPushPath("/logs/push");
        properties.setLokiPath("/loki/custom/push");

        properties.validate(new MockEnvironment().withProperty("sca4dj.gateway.secret", "gateway-secret"));

        assertThat(properties.validatedLokiOrigin()).hasToString("http://localhost:3100");
        assertThat(properties.getPushPath()).isEqualTo("/logs/push");
        assertThat(properties.getLokiPath()).isEqualTo("/loki/custom/push");
    }

    @Test
    @DisplayName("Loki 写入密码不能复用 Gateway Secret")
    void rejectsGatewaySecretAsPassword() {
        LokiGatewayProperties properties = properties();
        properties.setPassword("gateway-secret");

        assertThatThrownBy(
                () -> properties.validate(
                        new MockEnvironment().withProperty("sca4dj.gateway.secret", "gateway-secret")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能与 Gateway Secret 相同");
    }

    @Test
    @DisplayName("写入路径不能包含 Gateway 路由模式")
    void rejectsPatternPath() {
        LokiGatewayProperties properties = properties();
        properties.setPushPath("/internal/logging/**");

        assertThatThrownBy(() -> properties.validate(new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("push-path");
    }

    /** 创建包含有效 Origin 和独立凭据的属性。 */
    private static LokiGatewayProperties properties() {
        LokiGatewayProperties properties = new LokiGatewayProperties();
        properties.setEnabled(true);
        properties.setLokiOrigin("http://localhost:3100");
        properties.setUsername("alloy");
        properties.setPassword("logging-secret");
        return properties;
    }
}
