package com.sca4d.iot.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.iot.api.model.ThingArrayValue;
import com.sca4d.iot.api.model.ThingObjectValue;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** 编解码已经按物模型校验的标准数据，并显式展开专用 object 与 array 值。 */
public class ThingDataJsonCodec {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final @NotNull ObjectMapper objectMapper;

    /** @param objectMapper 具体应用统一 JSON 映射器 */
    public ThingDataJsonCodec(@NotNull ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "Thing data ObjectMapper 不能为空");
    }

    /** @param values 已校验 code 到值 @return 规范 JSON object */
    @CheckReturnValue
    public @NotNull String encodeMap(@NotNull Map<String, ?> values) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        new TreeMap<>(Objects.requireNonNull(values, "Thing data 不能为空"))
                .forEach(
                        (key, value) -> normalized
                                .put(key, normalize(Objects.requireNonNull(value, "Thing data 值不能为空"))));
        return encode(normalized);
    }

    /** @param value 单个已校验值 @return 规范 JSON */
    @CheckReturnValue
    public @NotNull String encodeValue(@NotNull Object value) {
        return encode(normalize(Objects.requireNonNull(value, "Thing value 不能为空")));
    }

    /** @param json JSON object @return 普通不可修改 Map */
    @CheckReturnValue
    public @NotNull Map<String, Object> decodeMap(@NotNull String json) {
        try {
            return Map.copyOf(objectMapper.readValue(json, MAP_TYPE));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Thing data JSON 无法解析", exception);
        }
    }

    /** @param json 单个 JSON 值 @return 普通 Java JSON 值 */
    @CheckReturnValue
    public @NotNull Object decodeValue(@NotNull String json) {
        try {
            return Objects.requireNonNull(objectMapper.readValue(json, Object.class), "Thing value JSON 不能是 null");
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Thing value JSON 无法解析", exception);
        }
    }

    /** 递归转换专用值和 Instant。 */
    private static Object normalize(Object value) {
        if (value instanceof ThingObjectValue object) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            new TreeMap<>(object.fields()).forEach((key, field) -> result.put(key, normalize(field)));
            return result;
        }
        if (value instanceof ThingArrayValue array) {
            List<Object> result = new ArrayList<>();
            array.elements().forEach(element -> result.add(normalize(element)));
            return List.copyOf(result);
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        return value;
    }

    /** 编码已规范化的 JSON 值。 */
    private String encode(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Thing data 无法编码", exception);
        }
    }
}
