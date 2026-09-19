package com.sca4d.openapi.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 验证具体应用声明 OpenAPI 认证端点时的路径约束。 */
class OpenApiAuthenticationEndpointTest {

    @Test
    @DisplayName("保留合法的 Client Credentials 端点路径")
    void keepsValidClientCredentialsPath() {
        OpenApiAuthenticationEndpoint endpoint = OpenApiAuthenticationEndpoint
                .clientCredentials(" /partner/oauth/token ");

        assertThat(endpoint.path()).isEqualTo("/partner/oauth/token");
    }

    @Test
    @DisplayName("拒绝根路径、尾部斜杠、重复斜杠、查询字符串和片段")
    void rejectsAmbiguousPaths() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OpenApiAuthenticationEndpoint.clientCredentials("/"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OpenApiAuthenticationEndpoint.clientCredentials("/oauth/token/"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OpenApiAuthenticationEndpoint.clientCredentials("/oauth//token"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OpenApiAuthenticationEndpoint.clientCredentials("/oauth/token?realm=partner"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OpenApiAuthenticationEndpoint.clientCredentials("/oauth/token#client"));
    }
}
