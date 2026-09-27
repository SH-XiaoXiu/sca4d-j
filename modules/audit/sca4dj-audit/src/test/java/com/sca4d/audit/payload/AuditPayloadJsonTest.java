package com.sca4d.audit.payload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.audit.config.AuditProperties;
import com.sca4d.core.api.audit.AuditPayload;
import com.sca4d.core.api.audit.AuditPayloadCodec;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 验证审计 payload 的 Raw JSON、脱敏、大小限制和可选 Codec。 */
class AuditPayloadJsonTest {

    @Test
    @DisplayName("没有 Codec 时保存 Raw JSON 并递归掩码敏感字段")
    void writesRedactedRawJsonWithoutCodec() {
        AuditPayloadJson json = payloadJson(List.of(), 65_536);

        EncodedAuditPayload encoded = Objects.requireNonNull(
                json.encode(
                        new RawPayload(
                                "visible",
                                Map.of("accessToken", "secret-token", "nested", Map.of("passwordHash", "hash")))));

        assertThat(encoded).isNotNull();
        assertThat(encoded.type()).isEqualTo("raw");
        assertThat(encoded.version()).isEqualTo(1);
        assertThat(encoded.json()).contains("\"name\":\"visible\"");
        assertThat(encoded.json()).contains("\"accessToken\":\"***\"");
        assertThat(encoded.json()).contains("\"passwordHash\":\"***\"");
        assertThat(encoded.json()).doesNotContain("secret-token", "hash");
        assertThat(json.decode(encoded.type(), encoded.version(), encoded.json()).decoded()).isEmpty();
    }

    @Test
    @DisplayName("超过字节限制时保存固定截断 JSON")
    void replacesOversizedPayloadWithTruncationJson() {
        AuditPayloadJson json = payloadJson(List.of(), 64);

        EncodedAuditPayload encoded = Objects.requireNonNull(
                json.encode(new RawPayload("内容".repeat(100), Map.of())));

        assertThat(encoded).isNotNull();
        assertThat(encoded.json()).isEqualTo("{\"truncated\":true,\"reason\":\"payload too large\"}");
    }

    @Test
    @DisplayName("存在精确 Codec 时按类型版本往返强类型 payload")
    void roundTripsPayloadWithExactCodec() {
        AuditPayloadJson json = payloadJson(List.of(new TypedPayloadCodec()), 65_536);

        EncodedAuditPayload encoded = Objects.requireNonNull(json.encode(new TypedPayload("changed")));
        AuditPayloadData decoded = json.decode(encoded.type(), encoded.version(), encoded.json());

        assertThat(encoded.type()).isEqualTo("platform.profile");
        assertThat(encoded.version()).isEqualTo(1);
        assertThat(decoded.decoded()).containsInstanceOf(TypedPayload.class);
        assertThat(((TypedPayload) decoded.decoded().orElseThrow()).value()).isEqualTo("changed");
    }

    @Test
    @DisplayName("拒绝静默忽略空白敏感字段配置")
    void rejectsBlankSensitiveFieldConfiguration() {
        AuditProperties properties = new AuditProperties();

        assertThatThrownBy(() -> properties.setSensitiveFields(List.of("token", " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("敏感字段不能为空白");
    }

    private static AuditPayloadJson payloadJson(
            List<AuditPayloadCodec<?>> codecs,
            int maxPayloadBytes) {
        AuditProperties properties = new AuditProperties();
        properties.setMaxPayloadBytes(maxPayloadBytes);
        return new AuditPayloadJson(
                new ObjectMapper(),
                new AuditPayloadRegistry(codecs),
                properties);
    }

    /** 提供未注册 Codec 时直接转换的测试 payload。 */
    @SuppressWarnings({"ClassCanBeRecord", "unused"}) // Jackson 通过反射读取 getter，项目禁止使用 Java record。
    private static final class RawPayload implements AuditPayload {
        private final String name;

        private final Map<String, Object> details;

        private RawPayload(String name, Map<String, Object> details) {
            this.name = name;
            this.details = details;
        }

        /** @return 普通可见字段 */
        public String getName() {
            return name;
        }

        /** @return 包含嵌套敏感字段的对象 */
        public Map<String, Object> getDetails() {
            return details;
        }
    }

    /** 提供版本化 Codec 往返的测试 payload。 */
    @SuppressWarnings("ClassCanBeRecord") // 项目禁止使用 Java record，保留普通测试类型。
    private static final class TypedPayload implements AuditPayload {
        private final String value;

        private TypedPayload(String value) {
            this.value = value;
        }

        /** @return 测试值 */
        public String value() {
            return value;
        }
    }

    /** 把 `TypedPayload` 编码为稳定的 platform.profile V1 JSON。 */
    private static final class TypedPayloadCodec implements AuditPayloadCodec<TypedPayload> {
        /** {@inheritDoc} */
        @Override
        public @NotNull String payloadType() {
            return "platform.profile";
        }

        /** {@inheritDoc} */
        @Override
        public int version() {
            return 1;
        }

        /** {@inheritDoc} */
        @Override
        public @NotNull Class<TypedPayload> payloadClass() {
            return TypedPayload.class;
        }

        /** {@inheritDoc} */
        @Override
        public @NotNull JsonNode encode(
                @NotNull ObjectMapper objectMapper,
                @NotNull TypedPayload payload) {
            return objectMapper.createObjectNode().put("value", payload.value());
        }

        /** {@inheritDoc} */
        @Override
        public @NotNull TypedPayload decode(
                @NotNull ObjectMapper objectMapper,
                @NotNull JsonNode payload) {
            return new TypedPayload(payload.path("value").asText());
        }
    }
}
