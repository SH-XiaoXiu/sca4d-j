package com.sca4d.gateway.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

/** 验证 upstream YAML 的加载、校验、热更新和无效配置回退行为。 */
class GatewayUpstreamFileLoaderTest {

    private final GatewayUpstreamFileLoader loader = new GatewayUpstreamFileLoader();

    @TempDir
    Path tempDir;

    @Test
    void shouldLoadGatewayUpstreams() throws Exception {
        Path file = tempDir.resolve("gateway-upstreams.yml");
        Files.writeString(file, """
                sca4dj:
                  gateway:
                    upstreams:
                      sca4d-app:
                        health-path: /ready
                        healthy-threshold: 1
                        unhealthy-threshold: 2
                        instances:
                          - id: app-1
                            uri: http://10.0.0.1:8080
                            enabled: true
                            metadata:
                              zone: zone-a
                          - id: app-2
                            uri: https://10.0.0.2
                            draining: true
                """);

        GatewayUpstreamSnapshot snapshot = loader.load(file, 7);

        assertThat(snapshot.getVersion()).isEqualTo(7);
        GatewayUpstream upstream = snapshot.getUpstreams().get("sca4d-app");
        assertThat(upstream.getHealthPath()).isEqualTo("/ready");
        assertThat(upstream.getInstances()).hasSize(2);
        assertThat(upstream.getInstances().getFirst().getMetadata()).containsEntry("zone", "zone-a");
        assertThat(upstream.getInstances().get(1).isDraining()).isTrue();
    }

    @Test
    void shouldRejectDuplicateInstanceId() throws Exception {
        Path file = tempDir.resolve("gateway-upstreams.yml");
        Files.writeString(file, """
                sca4dj:
                  gateway:
                    upstreams:
                      sca4d-app:
                        instances:
                          - id: app-1
                            uri: http://10.0.0.1:8080
                          - id: app-1
                            uri: http://10.0.0.2:8080
                """);

        assertThatThrownBy(() -> loader.load(file, 1)).hasMessageContaining("重复实例 ID");
    }

    @Test
    void shouldRejectInvalidInstanceUriWithConfigurationContext() throws Exception {
        Path file = tempDir.resolve("gateway-upstreams.yml");
        Files.writeString(file, """
                sca4dj:
                  gateway:
                    upstreams:
                      sca4d-app:
                        instances:
                          - id: app-1
                            uri: "http://[invalid"
                """);

        assertThatThrownBy(() -> loader.load(file, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("服务 sca4d-app 的实例 app-1 地址不是合法 URI");
    }

    @Test
    void shouldRejectNonPositiveHealthTimeout() throws Exception {
        Path file = tempDir.resolve("gateway-upstreams.yml");
        Files.writeString(file, """
                sca4dj:
                  gateway:
                    upstreams:
                      sca4d-app:
                        connect-timeout: PT0S
                """);

        assertThatThrownBy(() -> loader.load(file, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("连接超时必须在 1ms 到 Integer.MAX_VALUE ms 之间");
    }
}
