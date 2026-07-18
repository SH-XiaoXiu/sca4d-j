package com.sca4d.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.common.exception.InternalServiceException;
import com.sca4d.common.i18n.MessageReference;
import com.sca4d.config.properties.Sca4djAuditProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 审计详情编解码入口。
 */
public class AuditCodec {

    /** 没有注册类型化 Codec 时写入审计 JSON 的内容类型。 */
    public static final String RAW_PAYLOAD_TYPE = "raw";

    private final ObjectMapper objectMapper;
    private final AuditPayloadRegistry registry;
    private final Sca4djAuditProperties properties;

    /**
     * 创建使用全局 JSON 契约和已注册载荷编解码器的审计编解码入口。
     *
     * @param objectMapper 应用全局 Jackson 配置
     * @param registry 审计载荷类型与版本注册表
     * @param properties 详情长度和敏感字段配置
     */
    public AuditCodec(
            @NotNull ObjectMapper objectMapper,
            @NotNull AuditPayloadRegistry registry,
            @NotNull Sca4djAuditProperties properties) {
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.properties = properties;
    }

    /**
     * 编码审计记录。
     *
     * @param record 审计记录
     * @return 审计详情 JSON
     */
    public @NotNull String encode(@NotNull AuditRecord record) {
        AuditPayload payload = record.getPayload() == null ? RawAuditPayload.empty() : record.getPayload();
        AuditPayloadCodec<?> codec = registry.find(payload);
        String payloadType = StringUtils.hasText(record.getPayloadType())
                ? record.getPayloadType().trim()
                : codec == null ? RAW_PAYLOAD_TYPE : codec.payloadType();
        int payloadVersion = record.getPayloadVersion() != null && record.getPayloadVersion() > 0
                ? record.getPayloadVersion()
                : codec == null ? 1 : codec.payloadVersion();

        AuditEnvelope envelope = new AuditEnvelope();
        envelope.setSchemaVersion(1);
        envelope.setPayloadType(payloadType);
        envelope.setPayloadVersion(payloadVersion);
        envelope.setActor(record.getActor());
        envelope.setTarget(record.getTarget());
        envelope.setRequest(record.getRequest());
        MessageReference summary = record.getSummary();
        if (summary != null) {
            envelope.setSummaryKey(summary.getKey());
            envelope.setSummaryArgs(summary.getArguments());
        }
        envelope.setPayload(redact(payloadNode(codec, payload)));
        envelope.setError(record.getError());
        return write(envelope, payloadType, payloadVersion, record);
    }

    /**
     * 解码审计详情。
     *
     * @param detailJson 审计详情 JSON
     * @return 已解码审计详情
     */
    public @NotNull AuditDecodedEnvelope decode(@Nullable String detailJson) {
        if (!StringUtils.hasText(detailJson)) {
            AuditEnvelope envelope = new AuditEnvelope();
            envelope.setPayloadType(RAW_PAYLOAD_TYPE);
            envelope.setPayload(JsonNodeFactory.instance.objectNode());
            return new AuditDecodedEnvelope(envelope, RawAuditPayload.empty());
        }
        try {
            AuditEnvelope envelope = objectMapper.readValue(detailJson, AuditEnvelope.class);
            JsonNode payloadNode = envelope.getPayload() == null ? JsonNodeFactory.instance.objectNode() : envelope.getPayload();
            AuditPayloadCodec<?> codec = registry.find(envelope.getPayloadType(), envelope.getPayloadVersion());
            if (codec == null) {
                return new AuditDecodedEnvelope(envelope, rawPayload(payloadNode));
            }
            return new AuditDecodedEnvelope(envelope, decode(codec, payloadNode));
        } catch (JsonProcessingException exception) {
            throw new InternalServiceException("审计详情反序列化失败", exception);
        }
    }

    /**
     * 跨越运行时泛型擦除调用匹配载荷类型的编码器。
     *
     * @param codec 已按载荷运行时类型匹配的编解码器；未知载荷时为 null
     * @param payload 待编码的审计载荷
     * @return 可写入审计信封的 JSON 节点
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private @NotNull JsonNode payloadNode(@Nullable AuditPayloadCodec codec, @NotNull AuditPayload payload) {
        if (codec == null) {
            return objectMapper.valueToTree(payload);
        }
        return codec.encode(objectMapper, payload);
    }

    /**
     * 跨越运行时泛型擦除调用类型和版本均匹配的载荷解码器。
     *
     * @param codec 已按信封类型和版本匹配的编解码器
     * @param payload 信封中的载荷 JSON 节点
     * @return 解码后的固定审计载荷
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private @NotNull AuditPayload decode(@NotNull AuditPayloadCodec codec, @NotNull JsonNode payload) {
        return codec.decode(objectMapper, payload);
    }

    /**
     * 序列化审计信封，并在详情超过配置长度时用明确的截断载荷替换原载荷。
     *
     * <p>操作者、目标、请求、摘要和错误仍保留，长度限制用于阻止业务载荷无限放大审计记录，不保证整个信封小于该值。
     *
     * @param envelope 已完成脱敏的审计信封
     * @param payloadType 最终载荷类型
     * @param payloadVersion 最终载荷版本
     * @param record 原始审计记录，用于保留摘要消息引用
     * @return 可持久化的 JSON 文本
     */
    private @NotNull String write(
            @NotNull AuditEnvelope envelope,
            @NotNull String payloadType,
            int payloadVersion,
            @NotNull AuditRecord record) {
        try {
            String json = objectMapper.writeValueAsString(envelope);
            if (properties.getMaxPayloadLength() <= 0 || json.length() <= properties.getMaxPayloadLength()) {
                return json;
            }
            ObjectNode truncated = JsonNodeFactory.instance.objectNode();
            truncated.put("truncated", true);
            truncated.put("reason", "payload too large");
            envelope.setPayload(truncated);
            envelope.setPayloadType(payloadType);
            envelope.setPayloadVersion(payloadVersion);
            MessageReference summary = record.getSummary();
            if (summary != null) {
                envelope.setSummaryKey(summary.getKey());
                envelope.setSummaryArgs(summary.getArguments());
            }
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            throw new InternalServiceException("审计详情序列化失败", exception);
        }
    }

    /**
     * 递归复制载荷 JSON，并将命中敏感字段配置的属性值替换为掩码。
     *
     * @param node 载荷编码器生成的 JSON 节点
     * @return 不共享可变 ObjectNode 或 ArrayNode 的脱敏副本
     */
    private @NotNull JsonNode redact(@NotNull JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            node.properties().forEach(entry -> {
                if (sensitive(entry.getKey())) {
                    result.put(entry.getKey(), "***");
                    return;
                }
                result.set(entry.getKey(), redact(entry.getValue()));
            });
            return result;
        }
        if (node.isArray()) {
            var array = JsonNodeFactory.instance.arrayNode();
            node.forEach(item -> array.add(redact(item)));
            return array;
        }
        return node;
    }

    /**
     * 按不区分大小写的包含规则判断字段名是否敏感。
     *
     * @param field 当前 JSON 属性名
     * @return 命中任一有效敏感字段片段时返回 true
     */
    private boolean sensitive(@NotNull String field) {
        String normalized = field.toLowerCase(Locale.ROOT);
        for (String sensitiveField : properties.getSensitiveFields()) {
            if (StringUtils.hasText(sensitiveField)
                    && normalized.contains(sensitiveField.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将未知载荷对象转换为保持字段顺序的通用审计载荷。
     *
     * @param payloadNode 未找到对应类型或版本编解码器的任意 JSON 节点
     * @return 可供管理端通用展示的键值载荷
     */
    @SuppressWarnings("unchecked")
    private @NotNull RawAuditPayload rawPayload(@NotNull JsonNode payloadNode) {
        if (!payloadNode.isObject()) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("value", objectMapper.convertValue(payloadNode, Object.class));
            return new RawAuditPayload(value);
        }
        Map<String, Object> values = objectMapper.convertValue(payloadNode, LinkedHashMap.class);
        return new RawAuditPayload(values);
    }
}
