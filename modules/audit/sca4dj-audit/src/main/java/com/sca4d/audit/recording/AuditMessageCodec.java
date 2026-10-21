package com.sca4d.audit.recording;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.audit.recording.mapper.EncodedAuditEntry;
import com.sca4d.core.api.audit.AuditResult;
import com.sca4d.core.api.messaging.MessageCodec;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 读取和写入审计消息 v1 载荷；新消息由 {@link AuditMessageV2Codec} 写入。 */
public class AuditMessageCodec implements MessageCodec<AuditMessage> {
    private final ObjectMapper json;

    /** 使用 Messaging 同一 JSON 基础契约，保留审计消息 v1 的字段。 */
    public AuditMessageCodec(ObjectMapper json) {
        this.json = Objects.requireNonNull(json);
    }

    @Override
    public @NotNull TreeCode code() {
        return AuditMessage.CODE;
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public @NotNull Class<AuditMessage> valueType() {
        return AuditMessage.class;
    }

    @Override
    public byte @NotNull [] encode(@NotNull AuditMessage message) {
        EncodedAuditEntry e = message.entry();
        ObjectNode wire = json.createObjectNode();
        wire.put("requestId", e.requestId())
                .put("subjectId", e.subjectId())
                .put("accountId", e.accountId())
                .put("principalId", e.principalId())
                .put("operation", e.operation())
                .put("targetType", e.targetType())
                .put("targetId", e.targetId())
                .put("result", e.result().name())
                .put("payloadType", e.payloadType())
                .put("payloadVersion", e.payloadVersion())
                .put("payloadJson", e.payloadJson())
                .put("httpMethod", e.httpMethod())
                .put("httpPath", e.httpPath())
                .put("clientIp", e.clientIp())
                .put("userAgent", e.userAgent())
                .put("occurredAt", e.occurredAt().toString())
                .put("instanceId", e.instanceId());
        try {
            return json.writeValueAsBytes(wire);
        } catch (IOException failure) {
            throw new IllegalStateException("审计消息无法编码", failure);
        }
    }

    @Override
    public @NotNull AuditMessage decode(byte @NotNull [] bytes) {
        try {
            JsonNode w = json.readTree(bytes);
            if (!w.isObject()) {
                throw new IllegalArgumentException("审计消息必须是 JSON 对象");
            }
            Long account = w.required("accountId").isNull() ? null : positive(w, "accountId");
            Integer payloadVersion = w.required("payloadVersion").isNull()
                    ? null
                    : Math.toIntExact(positive(w, "payloadVersion"));
            String targetType = text(w, "targetType");
            String targetId = text(w, "targetId");
            String payloadType = text(w, "payloadType");
            String payloadJson = text(w, "payloadJson");
            String httpMethod = text(w, "httpMethod");
            String httpPath = text(w, "httpPath");
            String ip = text(w, "clientIp");
            if ((targetType == null) != (targetId == null)
                    || (payloadType == null) != (payloadVersion == null)
                    || (payloadType == null) != (payloadJson == null)
                    || (httpMethod == null) != (httpPath == null)
                    || (httpMethod == null) != (ip == null)) {
                throw new IllegalArgumentException("审计消息可选字段组合不完整");
            }
            if (payloadJson != null) {
                json.readTree(payloadJson);
            }
            String legacyId = UUID.nameUUIDFromBytes(bytes).toString();
            return new AuditMessage(
                    new FrozenAuditEntry(
                            new EncodedAuditEntry(
                                    0,
                                    legacyId,
                                    legacyId,
                                    null,
                                    w.required("requestId").requireNonNull().textValue(),
                                    positive(w, "subjectId"),
                                    account,
                                    positive(w, "principalId"),
                                    w.required("operation").requireNonNull().textValue(),
                                    targetType,
                                    targetId,
                                    AuditResult.valueOf(w.required("result").textValue()),
                                    payloadType,
                                    payloadVersion,
                                    payloadJson,
                                    httpMethod,
                                    httpPath,
                                    ip,
                                    text(w, "userAgent"),
                                    Instant.parse(w.required("occurredAt").textValue()),
                                    text(w, "instanceId")),
                            List.of()));
        } catch (IOException | RuntimeException failure) {
            throw new IllegalArgumentException("审计消息无法解码", failure);
        }
    }

    /** 可选文本必须显式为 null 或文本，不将数字转换为协议字段。 */
    private static String text(JsonNode wire, String field) {
        JsonNode value = wire.required(field);
        if (!value.isNull() && !value.isTextual()) {
            throw new IllegalArgumentException("审计字段必须是文本: " + field);
        }
        return value.isNull() ? null : value.textValue();
    }

    /** 身份与结构版本使用精确的正整数，不容许浮点数截断。 */
    private static long positive(JsonNode wire, String field) {
        JsonNode value = wire.required(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) {
            throw new IllegalArgumentException("审计字段必须是正整数: " + field);
        }
        return value.longValue();
    }
}
