package com.sca4d.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.gateway.GatewayCallbackCode;
import com.sca4d.core.api.gateway.GatewayConstants;
import com.sca4d.core.api.setting.ConfiguredConstants;
import com.sca4d.gateway.registration.GatewayInstanceRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.loadbalancer.DefaultRequest;
import org.springframework.cloud.client.loadbalancer.RequestData;
import org.springframework.cloud.client.loadbalancer.RequestDataContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.LinkedMultiValueMap;

import java.net.URI;
import java.util.Map;

/** 验证回调请求不会回退到普通 API 实例。 */
class GatewayLoadBalancerTest {

    @BeforeAll
    static void initializeGatewayConstants() {
        ConfiguredConstants.initialize(GatewayConstants.class, ignored -> null);
    }

    @Test
    @DisplayName("回调没有匹配实例时返回空选择")
    void doesNotFallbackCallbackToApiInstance() {
        GatewayInstanceRegistry registry = mock(GatewayInstanceRegistry.class);
        when(registry.selectCallback(new GatewayCallbackCode("iot.emqx.connections"))).thenReturn(null);
        GatewayLoadBalancer loadBalancer = new GatewayLoadBalancer(registry, "sca4d-server");
        RequestData data = new RequestData(
                HttpMethod.POST,
                URI.create("http://localhost/callbacks/iot.emqx.connections"),
                new HttpHeaders(),
                new LinkedMultiValueMap<>(),
                Map.of());

        var response = loadBalancer.choose(new DefaultRequest<>(new RequestDataContext(data))).block();

        assertThat(response).isNotNull();
        assertThat(response.hasServer()).isFalse();
        verify(registry, never()).selectApi("/");
    }
}
