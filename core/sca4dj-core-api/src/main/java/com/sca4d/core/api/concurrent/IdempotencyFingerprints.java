package com.sca4d.core.api.concurrent;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.core.api.exception.CoreApiException;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.TreeMap;

/** 把方法参数转换成字段顺序稳定的 JSON，并生成不暴露原始参数的幂等摘要。 */
public class IdempotencyFingerprints {

    private final ObjectMapper objectMapper;

    /**
     * 创建幂等参数规范化入口。
     *
     * @param objectMapper 应用统一的 JSON 映射器
     */
    public IdempotencyFingerprints(@NotNull ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
    }

    /**
     * 返回字段顺序稳定、保留数组业务顺序的参数 JSON。
     *
     * @param arguments 业务方法实参数组
     * @return 规范化参数 JSON
     * @throws CoreApiException 参数不能稳定转换成 JSON 时抛出
     */
    public @NotNull String canonicalArguments(@NotNull Object[] arguments) {
        try {
            JsonNode argumentsNode = canonicalize(
                    objectMapper.valueToTree(
                            Objects.requireNonNull(arguments, "业务方法实参不能为空")));
            return objectMapper.writeValueAsString(argumentsNode);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new CoreApiException("业务方法参数无法生成稳定幂等指纹", exception);
        }
    }

    /**
     * 返回规范化参数 JSON 的 SHA-256 摘要。
     *
     * @param arguments 业务方法实参数组
     * @return 不包含原始参数的十六进制摘要
     */
    public @NotNull String argumentsDigest(@NotNull Object[] arguments) {
        return DigestUtil.sha256Hex(canonicalArguments(arguments));
    }

    /** 递归排序 JSON 对象字段，同时保留数组元素的业务顺序。 */
    private JsonNode canonicalize(JsonNode value) {
        if (value.isObject()) {
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            value.properties().forEach(field -> fields.put(field.getKey(), field.getValue()));
            ObjectNode result = objectMapper.createObjectNode();
            fields.forEach((name, child) -> result.set(name, canonicalize(child)));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            value.forEach(child -> result.add(canonicalize(child)));
            return result;
        }
        return value;
    }
}
