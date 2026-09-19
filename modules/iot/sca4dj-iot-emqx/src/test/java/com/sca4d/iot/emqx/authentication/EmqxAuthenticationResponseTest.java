package com.sca4d.iot.emqx.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 验证 EMQX HTTP Authenticator 的外部 JSON 字段和 ACL 顺序。 */
class EmqxAuthenticationResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("deny 响应明确拒绝且不包含失效时间")
    void serializesDenyResponse() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(EmqxAuthenticationResponse.deny()));

        assertThat(json.path("result").textValue()).isEqualTo("deny");
        assertThat(json.path("is_superuser").booleanValue()).isFalse();
        assertThat(json.path("client_attrs").isObject()).isTrue();
        assertThat(json.path("acl").isArray()).isTrue();
        assertThat(json.has("expire_at")).isFalse();
    }

    @Test
    @DisplayName("allow 响应保留 ACL 顺序和 Unix 秒失效时间")
    void serializesAllowResponseAndAclOrder() throws Exception {
        EmqxAuthenticationResponse response = EmqxAuthenticationResponse.allow(
                Map.of("device_id", "7"),
                List.of(
                        new EmqxAclRule("allow", "publish", "demo/7/reported", List.of(1), false),
                        new EmqxAclRule("deny", "all", "#", List.of(0, 1, 2), null)),
                Instant.parse("2026-08-02T10:00:00Z"));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(response));

        assertThat(json.path("result").textValue()).isEqualTo("allow");
        assertThat(json.path("client_attrs").path("device_id").textValue()).isEqualTo("7");
        assertThat(json.path("acl").get(0).path("permission").textValue()).isEqualTo("allow");
        assertThat(json.path("acl").get(1).path("permission").textValue()).isEqualTo("deny");
        assertThat(json.path("expire_at").longValue())
                .isEqualTo(Instant.parse("2026-08-02T10:00:00Z").getEpochSecond());
    }
}
