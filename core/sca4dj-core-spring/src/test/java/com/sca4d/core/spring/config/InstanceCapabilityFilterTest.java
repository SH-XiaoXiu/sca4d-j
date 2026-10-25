package com.sca4d.core.spring.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.core.api.gateway.GatewayConstants;
import com.sca4d.core.api.identity.SystemPrincipals;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.runtime.InstanceCapabilities;
import com.sca4d.core.api.runtime.InstanceCapability;
import com.sca4d.core.api.setting.ConfiguredConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 验证实例未启用 IOT:API 时，直接访问 /iot 路径返回 503；MAIN:API 路径、/iot-other 和就绪检查路径继续放行。 */
class InstanceCapabilityFilterTest {

    @BeforeAll
    static void initializeGatewayConstants() {
        ConfiguredConstants.initialize(GatewayConstants.class, ignored -> null);
    }

    @Test
    void rejectsDisabledIotRoutesButAllowsMainApiAndReadiness() throws Exception {
        InstanceProperties properties = new Binder(
                new MapConfigurationPropertySource(
                        Map.of("sca4dj.instance.capabilities[0]", "MAIN:API")))
                .bind("sca4dj.instance", Bindable.of(InstanceProperties.class))
                .get();
        InstanceCapabilities capabilities = new InstanceCapabilities(
                List.of(
                        new InstanceCapability(TreeCode.ofColon("MAIN:API"), SystemPrincipals.MAIN, List.of("/")),
                        new InstanceCapability(
                                TreeCode.ofColon("IOT:API"),
                                TreeCode.ofSegment("IOT"),
                                List.of("/iot"))),
                Objects.requireNonNull(properties.getCapabilities()).stream().map(TreeCode::ofColon).toList());
        InstanceCapabilityFilter filter = new InstanceCapabilityFilter(capabilities);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/iot/devices"), response, chain);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(chain.getRequest()).isNull();
        for (String path : List.of("/users", "/iot-other", "/actuator/health/readiness")) {
            MockFilterChain allowed = new MockFilterChain();
            filter.doFilter(new MockHttpServletRequest("GET", path), new MockHttpServletResponse(), allowed);
            assertThat(allowed.getRequest()).isNotNull();
        }
    }
}
