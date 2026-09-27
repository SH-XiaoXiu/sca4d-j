package com.sca4d.gateway.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.gateway.GatewayCallbackCode;
import com.sca4d.core.api.gateway.GatewayRegistrationRequest;
import com.sca4d.gateway.config.GatewayProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

/** 验证 Gateway 租约替换保护和回调编码筛选。 */
class GatewayInstanceRegistryTest {

    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");

    @Test
    void selectsCapabilityByPathAndNeverFallsBackToTheMainInstance() {
        GatewayInstanceRegistry registry = registry();
        Map<String, Set<String>> requirements = Map.of("/", Set.of("MAIN:API"), "/iot", Set.of("IOT:API"));
        GatewayInstance main = registry.register(
                new GatewayRegistrationRequest(
                        "main",
                        URI.create("http://localhost:8081"),
                        "/ready",
                        Set.of(),
                        Set.of("MAIN:API"),
                        requirements));
        GatewayInstance iot = registry.register(
                new GatewayRegistrationRequest(
                        "iot",
                        URI.create("http://localhost:8082"),
                        "/ready",
                        Set.of(),
                        Set.of("IOT:API"),
                        requirements));
        registry.readiness(main.instanceId(), main.leaseId(), true);
        registry.readiness(iot.instanceId(), iot.leaseId(), true);
        assertThat(registry.selectApi("/api/iot/devices")).isEqualTo(iot);
        assertThat(registry.selectApi("/users")).isEqualTo(main);
        assertThat(registry.selectApi("/iot-other")).isEqualTo(main);
        assertThat(registry.remove(iot.instanceId(), iot.leaseId())).isTrue();
        assertThat(registry.selectApi("/iot/devices")).isNull();
    }

    @Test
    @DisplayName("旧租约不能续租或删除替换注册")
    void protectsReplacementFromOldLease() {
        GatewayInstanceRegistry registry = registry();
        GatewayInstance old = registry.register(request("server-1", 8081, "iot.emqx.authenticate"));
        GatewayInstance replacement = registry.register(request("server-1", 8082, "iot.emqx.authenticate"));

        assertThat(registry.renew(old.instanceId(), old.leaseId())).isFalse();
        assertThat(registry.remove(old.instanceId(), old.leaseId())).isFalse();
        assertThat(registry.leasedInstances()).containsExactly(replacement);
    }

    @Test
    @DisplayName("回调只轮询声明该编码的健康实例")
    void selectsOnlyHealthyCapableInstances() {
        GatewayInstanceRegistry registry = registry();
        GatewayInstance authentication = registry.register(request("server-a", 8081, "iot.emqx.authenticate"));
        GatewayInstance connections = registry.register(request("server-b", 8082, "iot.emqx.connections"));
        registry.readiness(authentication.instanceId(), authentication.leaseId(), true);
        registry.readiness(connections.instanceId(), connections.leaseId(), true);

        assertThat(registry.selectCallback(new GatewayCallbackCode("iot.emqx.authenticate"))).isEqualTo(authentication);
        assertThat(registry.selectCallback(new GatewayCallbackCode("iot.emqx.connections"))).isEqualTo(connections);
        assertThat(registry.selectCallback(new GatewayCallbackCode("missing.callback"))).isNull();
    }

    @Test
    @DisplayName("Server 基础地址不能包含会被 LoadBalancer 忽略的路径")
    void rejectsBaseUrlPath() {
        GatewayRegistrationRequest validRequest = request("server-1", 8081, "iot.emqx.authenticate");
        GatewayRegistrationRequest invalidRequest = new GatewayRegistrationRequest(
                validRequest.getInstanceId(),
                URI.create("http://localhost:8081/server-root"),
                validRequest.getReadinessPath(),
                validRequest.getCallbackCodes(),
                Set.of(),
                Map.of());

        assertThatThrownBy(() -> assertThat(registry().register(invalidRequest)).isNotNull())
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 创建具有固定时钟和 30 秒 TTL 的注册表。 */
    private static GatewayInstanceRegistry registry() {
        GatewayProperties properties = new GatewayProperties();
        properties.setSecret("secret");
        properties.setLeaseTtl(Duration.ofSeconds(30));
        return new GatewayInstanceRegistry(properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** 创建一个声明单个回调的有效注册请求。 */
    private static GatewayRegistrationRequest request(String instanceId, int port, String callbackCode) {
        return new GatewayRegistrationRequest(
                instanceId,
                URI.create("http://localhost:" + port),
                "/actuator/health/readiness",
                Set.of(callbackCode),
                Set.of(),
                Map.of());
    }
}
