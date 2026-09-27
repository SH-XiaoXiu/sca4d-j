package com.sca4d.auth.totp.enrollment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.core.api.security.EncryptedSecret;

import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/** 以显式结构编解码只含加密 Secret 的 enrollment 状态。 */
class TotpEnrollmentCodec {

    private final ObjectMapper objectMapper;

    TotpEnrollmentCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "TOTP enrollment JSON 映射器不能为空");
    }

    String encode(TotpEnrollmentState state) {
        // Redis 状态只保存 key-id 与密文；Base32 Secret 明文和 provisioning URI 从不进入 JSON。
        // schema version 与状态版本分开：前者控制格式兼容，后者用于 Lua 的一次性消费 CAS。
        ObjectNode root = objectMapper.createObjectNode();
        root.put("version", 1);
        root.put("enrollmentId", state.id());
        root.put("accountId", state.accountId());
        root.put("secretKeyId", state.secret().keyId());
        root.put("secretCiphertext", Base64.getEncoder().encodeToString(state.secret().ciphertext()));
        root.put("expiresAt", state.expiresAt().toString());
        root.put("state", state.status().name());
        root.put("stateVersion", state.version());
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("TOTP enrollment 编码失败", exception);
        }
    }

    TotpEnrollmentState decode(String encoded) {
        try {
            // 所有字段经过显式类型和范围校验，不依赖 Jackson 对内部类的宽松反射绑定。
            JsonNode root = objectMapper.readTree(encoded);
            if (root.path("version").asInt() != 1) {
                throw new IllegalStateException("TOTP enrollment 版本不受支持");
            }
            return new TotpEnrollmentState(
                    requiredText(root, "enrollmentId"),
                    requiredAccountId(root),
                    new EncryptedSecret(
                            requiredText(root, "secretKeyId"),
                            Base64.getDecoder().decode(requiredText(root, "secretCiphertext"))),
                    Instant.parse(requiredText(root, "expiresAt")),
                    TotpEnrollmentStatus.valueOf(requiredText(root, "state")),
                    requiredNonNegativeLong(root, "stateVersion"));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("TOTP enrollment 内容无效", exception);
        }
    }

    private static String requiredText(JsonNode node, String name) {
        String value = node.path(name).asText();
        if (value.isBlank()) {
            throw new IllegalStateException("TOTP enrollment 缺少字段: " + name);
        }
        return value;
    }

    private static long requiredAccountId(JsonNode node) {
        long value = requiredNonNegativeLong(node, "accountId");
        if (value == 0) {
            throw new IllegalStateException("TOTP enrollment 字段必须为正整数: accountId");
        }
        return value;
    }

    private static long requiredNonNegativeLong(JsonNode node, String name) {
        JsonNode value = node.path(name);
        if (!value.canConvertToLong() || value.asLong() < 0) {
            throw new IllegalStateException("TOTP enrollment 字段必须为非负整数: " + name);
        }
        return value.asLong();
    }
}
