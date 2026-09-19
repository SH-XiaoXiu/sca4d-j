package com.sca4d.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 验证 Gateway 日志写入路由的可选配置和目标地址限制。 */
class GatewayPropertiesTest {

    @Test
    @DisplayName("日志写入未启用时不要求 Loki 配置")
    void doesNotRequireLoggingConfigurationWhenDisabled() {
        GatewayProperties properties = baseProperties();

        properties.validate();

        assertThat(properties.getLogging().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("日志写入启用时要求 HTTP Origin 和独立凭据")
    void requiresLoggingOriginAndCredentialWhenEnabled() {
        GatewayProperties properties = baseProperties();
        properties.getLogging().setEnabled(true);
        properties.getLogging().setLokiOrigin("http://localhost:3100/loki");
        properties.getLogging().setUsername("alloy");
        properties.getLogging().setPassword("logging-secret");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP(S) Origin");

        properties.getLogging().setLokiOrigin("http://localhost:3100/");
        properties.validate();
        assertThat(properties.getLogging().requireLokiOrigin().toString())
                .isEqualTo("http://localhost:3100");
    }

    @Test
    @DisplayName("日志写入密码不能复用 Gateway Secret")
    void rejectsGatewaySecretAsLoggingPassword() {
        GatewayProperties properties = baseProperties();
        properties.getLogging().setEnabled(true);
        properties.getLogging().setLokiOrigin("http://localhost:3100");
        properties.getLogging().setUsername("alloy");
        properties.getLogging().setPassword("gateway-secret");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能与 Gateway Secret 相同");
    }

    /** 创建包含有效 Gateway 控制配置的属性。 */
    private static GatewayProperties baseProperties() {
        GatewayProperties properties = new GatewayProperties();
        properties.setSecret("gateway-secret");
        return properties;
    }
}
