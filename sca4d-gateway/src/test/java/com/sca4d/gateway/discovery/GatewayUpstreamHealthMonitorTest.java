package com.sca4d.gateway.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** 验证网关按固定间隔检查 upstream，并在健康状态变化时更新注册表。 */
class GatewayUpstreamHealthMonitorTest {

    @Test
    void shouldCompleteHealthRoundBeforeUpdatingRoutingState() {
        DisposableServer server = HttpServer.create()
                .port(0)
                .route(routes -> routes.get("/ready", (request, response) -> response.status(204).send()))
                .bindNow();
        try {
            GatewayInstance instance = new GatewayInstance(
                    "app-1", URI.create("http://127.0.0.1:" + server.port()), true, false, Map.of());
            GatewayUpstream upstream = new GatewayUpstream(
                    "sca4d-app",
                    "/ready",
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(1),
                    1,
                    1,
                    List.of(instance));
            GatewayUpstreamSnapshot snapshot = new GatewayUpstreamSnapshot(1, Map.of("sca4d-app", upstream));
            GatewayUpstreamRegistry registry = mock(GatewayUpstreamRegistry.class);
            when(registry.snapshot()).thenReturn(snapshot);
            GatewayUpstreamHealthState healthState = new GatewayUpstreamHealthState();

            new GatewayUpstreamHealthMonitor(registry, healthState).checkHealth();

            assertThat(healthState.healthy(upstream.healthKey(instance))).isTrue();
        } finally {
            server.disposeNow();
        }
    }
}
