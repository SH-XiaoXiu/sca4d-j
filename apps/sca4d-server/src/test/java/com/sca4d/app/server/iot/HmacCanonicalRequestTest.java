package com.sca4d.app.server.iot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** 验证 Sca4d Server 的 Device HTTP MAC 使用稳定 canonical 格式。 */
class HmacCanonicalRequestTest {

    @Test
    @DisplayName("canonical request 使用 IoT HMAC namespace")
    void usesIotHmacNamespace() {
        HmacCanonicalRequest request = new HmacCanonicalRequest(
                42,
                "post",
                "/api/iot/device-sessions",
                "",
                new byte[0],
                Instant.ofEpochSecond(1_786_000_000L),
                "0123456789abcdef");

        assertThat(new String(request.canonicalBytes(), StandardCharsets.UTF_8))
                .isEqualTo("""
                        IOT-HMAC-SHA256
                        42
                        POST
                        /api/iot/device-sessions

                        e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
                        1786000000
                        0123456789abcdef""");
    }
}
