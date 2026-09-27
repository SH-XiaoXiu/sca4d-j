package com.sca4d.audit.payload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.audit.config.AuditProperties;
import com.sca4d.core.api.audit.AuditPayload;
import com.sca4d.core.api.audit.AuditPayloadCodec;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

/** 使用 Raw JSON 或可选 Codec 编码、脱敏、限制并读取审计 payload。 */
@ApiStatus.Internal
public class AuditPayloadJson {
    private static final Logger LOG = LoggerFactory.getLogger(AuditPayloadJson.class);

    static final String RAW_TYPE = "raw";

    static final int RAW_VERSION = 1;

    private final @NotNull ObjectMapper objectMapper;

    private final @NotNull AuditPayloadRegistry registry;

    private final @NotNull AuditProperties properties;

    /** 创建使用脚手架 JSON 契约的 payload 处理器。 */
    public AuditPayloadJson(
            @NotNull ObjectMapper objectMapper,
            @NotNull AuditPayloadRegistry registry,
            @NotNull AuditProperties properties) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "审计 JSON 映射器不能为空");
        this.registry = Objects.requireNonNull(registry, "审计 Codec 注册表不能为空");
        this.properties = Objects.requireNonNull(properties, "审计配置不能为空");
    }

    /** 把可选强类型载荷转换成可以直接写入数据库的 JSON。 */
    @Nullable
    public EncodedAuditPayload encode(@Nullable AuditPayload payload) {
        if (payload == null) {
            return null;
        }
        AuditPayloadCodec<?> codec = registry.find(payload);
        String type = codec == null ? RAW_TYPE : codec.payloadType();
        int version = codec == null ? RAW_VERSION : codec.version();
        LOG.trace(
                "审计载荷编码方式已选择 | payloadType={} payloadVersion={} codecRegistered={}",
                type,
                version,
                codec != null);
        JsonNode encoded = codec == null ? objectMapper.valueToTree(payload) : encode(codec, payload);
        String json = write(redact(encoded));
        int encodedBytes = json.getBytes(StandardCharsets.UTF_8).length;
        if (encodedBytes > properties.getMaxPayloadBytes()) {
            LOG.warn(
                    "审计载荷超过保存限制 | payloadType={} payloadVersion={} encodedBytes={} maxPayloadBytes={}",
                    type,
                    version,
                    encodedBytes,
                    properties.getMaxPayloadBytes());
            ObjectNode truncated = JsonNodeFactory.instance.objectNode();
            truncated.put("truncated", true);
            truncated.put("reason", "payload too large");
            json = write(truncated);
        }
        return new EncodedAuditPayload(type, version, json);
    }

    /** 返回原始 JSON，并在存在对应 Codec 且内容未截断时附加强类型结果。 */
    @NotNull
    public AuditPayloadData decode(@NotNull String type, int version, @NotNull String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            AuditPayload decoded = null;
            AuditPayloadCodec<?> codec = RAW_TYPE.equals(type) ? null : registry.find(type, version);
            if (codec != null && !node.path("truncated").asBoolean(false)) {
                decoded = decode(codec, node);
                LOG.trace(
                        "审计载荷已恢复为注册类型 | payloadType={} payloadVersion={}",
                        type,
                        version);
            } else if (!RAW_TYPE.equals(type) && codec == null) {
                LOG.trace(
                        "审计载荷没有匹配的读取编码器 | payloadType={} payloadVersion={}",
                        type,
                        version);
            }
            return new AuditPayloadData(type, version, json, decoded);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("审计 payload JSON 无法读取", exception);
        }
    }

    /** 使用 Codec 声明的精确载荷类检查输入，再生成待脱敏 JSON。 */
    private <P extends AuditPayload> @NotNull JsonNode encode(
            @NotNull AuditPayloadCodec<P> codec,
            @NotNull AuditPayload payload) {
        P checked = codec.payloadClass().cast(payload);
        return Objects.requireNonNull(codec.encode(objectMapper, checked), "审计 Codec 不能返回空 JSON");
    }

    /** 使用匹配持久化类型和版本的 Codec 恢复强类型载荷。 */
    private @NotNull AuditPayload decode(
            @NotNull AuditPayloadCodec<?> codec,
            @NotNull JsonNode payload) {
        return Objects.requireNonNull(codec.decode(objectMapper, payload), "审计 Codec 不能返回空载荷");
    }

    /** 递归复制 JSON，并把名称命中敏感字段片段的值替换为掩码。 */
    private @NotNull JsonNode redact(@NotNull JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            node.properties().forEach(entry -> {
                if (sensitive(entry.getKey())) {
                    result.put(entry.getKey(), "***");
                } else {
                    result.set(entry.getKey(), redact(entry.getValue()));
                }
            });
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            node.forEach(value -> result.add(redact(value)));
            return result;
        }
        return node.deepCopy();
    }

    /** 判断字段名是否包含配置中的任一敏感片段，匹配时忽略大小写。 */
    private boolean sensitive(@NotNull String field) {
        String normalized = field.toLowerCase(Locale.ROOT);
        return properties.getSensitiveFields()
                .stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::contains);
    }

    /** 把已经脱敏的 JSON 节点序列化为数据库保存文本。 */
    private @NotNull String write(@NotNull JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("审计 payload JSON 无法写入", exception);
        }
    }
}
