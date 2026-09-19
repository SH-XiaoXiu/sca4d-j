package com.sca4d.openapi.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 验证 OAuth Client Credentials 响应的外部 JSON 字段。 */
class OpenApiTokenResponseTest {

    @Test
    @DisplayName("使用 OAuth 标准字段名序列化 Token 响应")
    void serializesOAuthFieldNames() {
        OpenApiTokenResponse response = mock(OpenApiTokenResponse.class);
        when(response.getAccessToken()).thenReturn("access-token");
        when(response.getExpiresIn()).thenReturn(60);
        when(response.getScope()).thenReturn("iot:devices:read");
        when(response.getTokenType()).thenReturn("Bearer");

        JsonNode json = new ObjectMapper().valueToTree(response);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getExpiresIn()).isEqualTo(60);
        assertThat(response.getScope()).isEqualTo("iot:devices:read");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(json.size()).isEqualTo(4);
        assertThat(json.path("access_token").textValue()).isEqualTo(response.getAccessToken());
        assertThat(json.path("expires_in").intValue()).isEqualTo(response.getExpiresIn());
        assertThat(json.path("scope").textValue()).isEqualTo(response.getScope());
        assertThat(json.path("token_type").textValue()).isEqualTo(response.getTokenType());
        assertThat(json.has("accessToken")).isFalse();
        assertThat(json.has("expiresIn")).isFalse();
        assertThat(json.has("tokenType")).isFalse();
    }
}
