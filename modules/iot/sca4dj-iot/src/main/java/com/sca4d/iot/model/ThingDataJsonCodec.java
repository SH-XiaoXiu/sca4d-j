package com.sca4d.iot.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.iot.api.model.ThingArrayValue;
import com.sca4d.iot.api.model.ThingObjectValue;
import com.sca4d.iot.exception.IotException;
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

    /**
     * 创建物模型数据 Codec，之后所有编码和解析都使用该 ObjectMapper。
     *
     * @param objectMapper 具体应用统一 JSON 映射器，为 null 时抛出 {@link NullPointerException}
     */
    public ThingDataJsonCodec(@NotNull ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "Thing data ObjectMapper 不能为空");
    }

    /**
     * 按 code 升序把已校验的字段值编码为 JSON object，值中的 {@code ThingObjectValue}、{@code ThingArrayValue} 与 Instant 会先展开。
     *
     * @param values 已通过物模型校验的 code 到值映射，不能包含 null 值
     * @return 规范 JSON object 文本
     * @throws IotException 值无法编码为 JSON 时抛出
     */
    @CheckReturnValue
    public @NotNull String encodeMap(@NotNull Map<String, ?> values) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        new TreeMap<>(Objects.requireNonNull(values, "Thing data 不能为空"))
                .forEach(
                        (key, value) -> normalized
                                .put(key, normalize(Objects.requireNonNull(value, "Thing data 值不能为空"))));
        return encode(normalized);
    }

    /**
     * 把单个已通过物模型校验的值编码为 JSON 文本，专用 object、array 和 Instant 会先展开。
     *
     * @param value 已校验的物模型值，不能为 null
     * @return 规范 JSON 文本
     * @throws IotException 值无法编码为 JSON 时抛出
     */
    @CheckReturnValue
    public @NotNull String encodeValue(@NotNull Object value) {
        return encode(normalize(Objects.requireNonNull(value, "Thing value 不能为空")));
    }

    /**
     * 把 JSON object 解析为不可修改的普通 Java Map，嵌套值保持 Jackson 默认的 Java 类型。
     *
     * @param json JSON object 文本
     * @return 不可修改的顶层字段 Map
     * @throws IotException JSON 语法不合法或不是 object 时抛出
     */
    @CheckReturnValue
    public @NotNull Map<String, Object> decodeMap(@NotNull String json) {
        try {
            return Map.copyOf(objectMapper.readValue(json, MAP_TYPE));
        } catch (JsonProcessingException exception) {
            throw new IotException("Thing data JSON 无法解析", exception);
        }
    }

    /**
     * 把单个 JSON 值解析为普通 Java 值，JSON null 不被接受。
     *
     * @param json 单个 JSON 值文本
     * @return 普通 Java 值，不会为 null
     * @throws IotException JSON 语法不合法时抛出
     * @throws NullPointerException JSON 结果为 null 时抛出
     */
    @CheckReturnValue
    public @NotNull Object decodeValue(@NotNull String json) {
        try {
            return Objects.requireNonNull(objectMapper.readValue(json, Object.class), "Thing value JSON 不能是 null");
        } catch (JsonProcessingException exception) {
            throw new IotException("Thing value JSON 无法解析", exception);
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
            throw new IotException("Thing data 无法编码", exception);
        }
    }
}
