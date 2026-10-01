package com.sca4d.gateway.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sca4d.core.api.gateway.GatewayConstants;
import com.sca4d.core.api.gateway.GatewayRegistrationRequest;
import com.sca4d.core.api.setting.ConfiguredConstants;
import com.sca4d.gateway.config.GatewayProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Map;
import java.util.Set;

/** 验证控制 API 只接受单个正确 Secret 和相同的协议配置。 */
class GatewayRegistrationControllerTest {

    @BeforeAll
    static void initializeGatewayConstants() {
        ConfiguredConstants.initialize(GatewayConstants.class, ignored -> null);
    }

    @Test
    void rejectsProtocolMismatchBeforeCreatingLease() {
        GatewayInstanceRegistry registry = mock(GatewayInstanceRegistry.class);
        GatewayRegistrationController controller = controller(registry);
        HttpHeaders headers = new HttpHeaders();
        headers.set(GatewayConstants.SECRET_HEADER, "secret");
        GatewayRegistrationRequest request = new GatewayRegistrationRequest(
                "server-1",
                URI.create("http://localhost:8080"),
                "/actuator/health/readiness",
                "different-protocol",
                Set.of(),
                Set.of(),
                Map.of());

        assertThatThrownBy(() -> controller.register(headers, request))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(registry);
    }

    @Test
    void rejectsRepeatedSecretHeaderBeforeCreatingLease() {
        GatewayInstanceRegistry registry = mock(GatewayInstanceRegistry.class);
        GatewayRegistrationController controller = controller(registry);
        HttpHeaders headers = new HttpHeaders();
        headers.add(GatewayConstants.SECRET_HEADER, "secret");
        headers.add(GatewayConstants.SECRET_HEADER, "secret");

        assertThatThrownBy(() -> controller.register(headers, null))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verifyNoInteractions(registry);
    }

    /** 创建只用于校验请求的控制器。 */
    private static GatewayRegistrationController controller(GatewayInstanceRegistry registry) {
        GatewayProperties properties = new GatewayProperties();
        properties.setSecret("secret");
        return new GatewayRegistrationController(registry, properties);
    }
}
