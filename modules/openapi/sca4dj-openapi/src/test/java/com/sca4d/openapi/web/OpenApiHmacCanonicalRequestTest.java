package com.sca4d.openapi.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** 验证 OpenAPI MAC canonical request 的字段顺序和输入限制。 */
class OpenApiHmacCanonicalRequestTest {

    @Test
    @DisplayName("按固定顺序生成包含查询和请求体摘要的 canonical request")
    void createsCanonicalRequest() {
        OpenApiHmacCanonicalRequest request = new OpenApiHmacCanonicalRequest(
                "client-1",
                "post",
                "/openapi/devices",
                "a=1&b=2",
                "{}".getBytes(StandardCharsets.UTF_8),
                Instant.ofEpochSecond(1_700_000_000L),
                "0123456789abcdef");

        assertThat(new String(request.canonicalBytes(), StandardCharsets.UTF_8))
                .isEqualTo(
                        """
                                OPENAPI-HMAC-SHA256
                                client-1
                                POST
                                /openapi/devices
                                a=1&b=2
                                44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a
                                1700000000
                                0123456789abcdef\
                                """);
    }

    @Test
    @DisplayName("拒绝过短或包含控制字符的 nonce")
    void rejectsInvalidNonce() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> new OpenApiHmacCanonicalRequest(
                                "client-1",
                                "GET",
                                "/openapi/devices",
                                "",
                                new byte[0],
                                Instant.EPOCH,
                                "short"));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> new OpenApiHmacCanonicalRequest(
                                "client-1",
                                "GET",
                                "/openapi/devices",
                                "",
                                new byte[0],
                                Instant.EPOCH,
                                "0123456789abcde\n"));
    }
}
