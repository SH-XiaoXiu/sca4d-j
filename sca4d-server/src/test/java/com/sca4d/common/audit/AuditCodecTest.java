package com.sca4d.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.authentication.password.audit.PasswordLoginAuditPayload;
import com.sca4d.authentication.password.audit.PasswordLoginAuditPayloadCodec;
import com.sca4d.common.auth.CredentialType;
import com.sca4d.config.properties.Sca4djAuditProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

/** 验证审计内容的类型注册、JSON 序列化和反序列化。 */
class AuditCodecTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DemoPayloadCodec payloadCodec = new DemoPayloadCodec();
    private final AuditCodec codec = new AuditCodec(
            objectMapper,
            new AuditPayloadRegistry(List.of(payloadCodec)),
            new Sca4djAuditProperties());

    @Test
    void shouldEncodeTypedPayloadAndRedactSensitiveFields() {
        AuditRecord record = new AuditRecord();
        record.setPayload(new DemoPayload("alice", "secret"));

        String json = codec.encode(record);

        assertThat(json).contains("\"payloadType\":\"demo\"");
        assertThat(json).contains("\"password\":\"***\"");
        assertThat(json).doesNotContain("secret");
    }

    @Test
    void shouldDecodeTypedPayload() {
        AuditRecord record = new AuditRecord();
        record.setPayload(new DemoPayload("alice", "secret"));

        AuditDecodedEnvelope decoded = codec.decode(codec.encode(record));

        assertThat(decoded.getEnvelope().getPayloadType()).isEqualTo("demo");
        assertThat(decoded.getPayload()).isEqualTo(new DemoPayload("alice", "***"));
    }

    @Test
    void shouldKeepCredentialReferenceMetadataInTypedPayload() {
        AuditCodec credentialCodec = new AuditCodec(
                objectMapper,
                new AuditPayloadRegistry(List.of(new PasswordLoginAuditPayloadCodec())),
                new Sca4djAuditProperties());
        AuditRecord record = new AuditRecord();
        record.setPayload(new PasswordLoginAuditPayload()
                .setCredentialId(42L)
                .setCredentialType(CredentialType.PASSWORD)
                .setSubjectId(21L)
                .setLoginAccountMask("a***e"));

        AuditDecodedEnvelope decoded = credentialCodec.decode(credentialCodec.encode(record));

        assertThat(decoded.getPayload()).isInstanceOf(PasswordLoginAuditPayload.class);
        PasswordLoginAuditPayload payload = (PasswordLoginAuditPayload) decoded.getPayload();
        assertThat(payload.getCredentialId()).isEqualTo(42L);
        assertThat(payload.getCredentialType()).isEqualTo(CredentialType.PASSWORD);
        assertThat(payload.getSubjectId()).isEqualTo(21L);
        assertThat(payload.getLoginAccountMask()).isEqualTo("a***e");
    }

    @Test
    void shouldDecodeUnknownPayloadAsRawPayload() {
        String json = """
                {
                  "schemaVersion": 1,
                  "payloadType": "unknown",
                  "payloadVersion": 1,
                  "payload": {
                    "name": "demo"
                  }
                }
                """;

        AuditDecodedEnvelope decoded = codec.decode(json);

        assertThat(decoded.getPayload()).isInstanceOf(RawAuditPayload.class);
        assertThat(((RawAuditPayload) decoded.getPayload()).getValues()).containsEntry("name", "demo");
    }

    @Test
    void shouldDecodeUnknownArrayPayloadWithoutLosingOperationLog() {
        String json = """
                {
                  "schemaVersion": 1,
                  "payloadType": "unknown",
                  "payloadVersion": 1,
                  "payload": ["first", "second"]
                }
                """;

        AuditDecodedEnvelope decoded = codec.decode(json);

        assertThat(decoded.getPayload()).isInstanceOf(RawAuditPayload.class);
        assertThat(((RawAuditPayload) decoded.getPayload()).getValues())
                .containsEntry("value", List.of("first", "second"));
    }

    @Test
    void shouldIgnoreBlankSensitiveFieldConfiguration() {
        Sca4djAuditProperties properties = new Sca4djAuditProperties();
        properties.setSensitiveFields(List.of(" ", "password"));
        AuditCodec configuredCodec = new AuditCodec(
                objectMapper,
                new AuditPayloadRegistry(List.of(payloadCodec)),
                properties);
        AuditRecord record = new AuditRecord();
        record.setPayload(new DemoPayload("alice", "secret"));

        String json = configuredCodec.encode(record);

        assertThat(json).contains("\"username\":\"alice\"");
        assertThat(json).contains("\"password\":\"***\"");
    }

    /**
     * 用于验证审计载荷编解码和敏感字段脱敏行为的不可变载荷。
     */
    private static final class DemoPayload implements AuditPayload {

        private final String username;
        private final String password;

        /**
         * 创建携带登录名和敏感口令的测试载荷。
         *
         * @param username 用于验证普通字段序列化的登录名
         * @param password 用于验证脱敏规则的原始口令
         */
        @JsonCreator
        private DemoPayload(
                @JsonProperty("username") @NotNull String username,
                @JsonProperty("password") @NotNull String password) {
            this.username = username;
            this.password = password;
        }

        /**
         * 返回用于验证普通字段序列化的登录名。
         *
         * @return 测试载荷中的登录名
         */
        public @NotNull String getUsername() {
            return username;
        }

        /**
         * 返回用于验证脱敏规则的原始口令。
         *
         * @return 测试载荷中的原始口令
         */
        public @NotNull String getPassword() {
            return password;
        }

        /**
         * 比较两个测试载荷的登录名和口令。
         *
         * @param other 待比较对象
         * @return 登录名与口令均相同的测试载荷返回 {@code true}
         */
        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof DemoPayload payload)) {
                return false;
            }
            return Objects.equals(username, payload.username) && Objects.equals(password, payload.password);
        }

        /**
         * 计算与 {@link #equals(Object)} 一致的测试载荷哈希值。
         *
         * @return 登录名和口令的组合哈希值
         */
        @Override
        public int hashCode() {
            return Objects.hash(username, password);
        }
    }

    /** 用于验证审计载荷类型注册和反序列化分派的测试编解码器。 */
    private static final class DemoPayloadCodec implements AuditPayloadCodec<DemoPayload> {

        @Override
        public @NotNull String payloadType() {
            return "demo";
        }

        @Override
        public @NotNull Class<DemoPayload> payloadClass() {
            return DemoPayload.class;
        }
    }
}
