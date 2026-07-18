package com.sca4d.common.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.net.URI;

/** 验证外部 HTTP 端点的 HTTPS 和本机测试 HTTP 规则。 */
class HttpEndpointsTest {

    @Test
    void shouldAllowHttpsAndLoopbackHttpEndpoints() {
        assertThat(HttpEndpoints.parseSecure("https://id.example.com:8443/oauth/profile?view=full"))
                .isEqualTo(URI.create("https://id.example.com:8443/oauth/profile?view=full"));
        assertThat(HttpEndpoints.isSecure(URI.create("http://localhost:8080/callback"))).isTrue();
        assertThat(HttpEndpoints.isSecure(URI.create("http://127.0.0.1:8080/callback"))).isTrue();
        assertThat(HttpEndpoints.isSecure(URI.create("http://[::1]:8080/callback"))).isTrue();
    }

    @Test
    void shouldRejectUnsafeOrIncompleteEndpoints() {
        assertThat(HttpEndpoints.isSecure(null)).isFalse();
        assertThat(HttpEndpoints.isSecure(URI.create("/relative"))).isFalse();
        assertThat(HttpEndpoints.isSecure(URI.create("http://id.example.com/token"))).isFalse();
        assertThat(HttpEndpoints.isSecure(URI.create("https://user@id.example.com/token"))).isFalse();
        assertThat(HttpEndpoints.isSecure(URI.create("https://id.example.com/token#secret"))).isFalse();
        assertThat(HttpEndpoints.isSecure(URI.create("https:/missing-host"))).isFalse();
        assertThatThrownBy(() -> HttpEndpoints.parseSecure("not a uri"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("格式错误");
    }
}
