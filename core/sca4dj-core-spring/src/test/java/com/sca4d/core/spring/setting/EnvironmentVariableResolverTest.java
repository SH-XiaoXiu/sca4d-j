package com.sca4d.core.spring.setting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** 验证系统设置 ENV 后缀按显式前缀或应用名解析。 */
class EnvironmentVariableResolverTest {

    @Test
    void resolvesConfiguredPrefix() {
        EnvironmentPrefixProperties properties = new EnvironmentPrefixProperties();
        properties.setPrefix("SCA4D");

        EnvironmentVariableResolver resolver = new EnvironmentVariableResolver(
                new MockEnvironment().withProperty("spring.application.name", "sca4d-server"),
                properties);

        assertThat(resolver.resolve("AUTH_SESSION_TTL")).isEqualTo("SCA4D_AUTH_SESSION_TTL");
    }

    @Test
    void derivesPrefixFromApplicationNameWhenMissing() {
        EnvironmentPrefixProperties properties = new EnvironmentPrefixProperties();

        EnvironmentVariableResolver resolver = new EnvironmentVariableResolver(
                new MockEnvironment().withProperty("spring.application.name", "product-server"),
                properties);

        assertThat(resolver.prefix()).isEqualTo("PRODUCT_SERVER");
        assertThat(resolver.resolve("AUTH_SESSION_TTL")).isEqualTo("PRODUCT_SERVER_AUTH_SESSION_TTL");
    }
}
