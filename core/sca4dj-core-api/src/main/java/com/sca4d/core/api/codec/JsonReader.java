package com.sca4d.core.api.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.data.DataKey;
import com.sca4d.core.api.data.exception.DataAssertionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 严格读取一棵已解析的 JSON 树，供 {@link JsonCodec#decode} 使用。
 *
 * <p>读取器不可变，每个 {@code JsonReader} 对应树中的一个位置（可能不存在）。
 * 读取的默认规则都是严格的：
 * <ul>
 *   <li>不做类型转换，数字不会被读成文本，文本也不会被读成数字或布尔值，
 *       唯一的例外是 {@link BigDecimal} 同时接受 JSON 数字和数字文本；</li>
 *   <li>文本必须非空白；</li>
 *   <li>枚举按常量名精确匹配；</li>
 *   <li>调用 {@code allowOnly} 后，对象中出现未声明的字段会失败。</li>
 * </ul>
 *
 * <p>所有失败都抛出 {@link DataAssertionException}，其 {@code field} 是从根开始的完整路径，
 * 例如 {@code fields.PRICE.zh-CN.label} 或 {@code scopes[2].values[0]}；根节点路径为 {@code $}。
 * 异常信息只描述期望的类型和允许的取值，不包含 JSON 中的实际值。
 *
 * <p>支持读取的值类型为 {@link String}、{@link Integer}、{@link Long}、{@link Boolean}、
 * {@link BigDecimal}、枚举，以及这些类型的数组。其他类型会抛出 {@link IllegalArgumentException}，
 * 这是调用方代码错误，与数据是否合法无关。
 *
 * <pre>{@code
 * JsonReader root = JsonReader.parse(json);
 * root.allowOnly(NAME, PRICE, LABELS);
 * String name = root.required(NAME);
 * BigDecimal price = root.optional(PRICE).orElse(BigDecimal.ZERO);
 * for (Map.Entry<String, JsonReader> entry : root.object("labels").entries().entrySet()) {
 *     String language = entry.getKey();
 *     String label = entry.getValue().value(LABEL);
 * }
 * }</pre>
 */
public final class JsonReader {

    private static final String ROOT_PATH = "$";

    private static final ObjectMapper PARSER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    /** 当前位置的节点；字段不存在或根节点为空时为 {@code null}，JSON {@code null} 为 NullNode。 */
    private final @Nullable JsonNode node;

    private final String path;

    private JsonReader(@Nullable JsonNode node, String path) {
        this.node = node;
        this.path = path;
    }

    /**
     * 以已解析的 JSON 树创建根读取器。
     *
     * @param node 根节点；为 {@code null} 时不立即失败，第一次读取时报告缺少内容
     * @return 路径为 {@code $} 的读取器
     */
    public static @NotNull JsonReader of(@Nullable JsonNode node) {
        return new JsonReader(node, ROOT_PATH);
    }

    /**
     * 解析 JSON 文本并创建根读取器。
     *
     * @param json 完整的 JSON 文本，不允许尾随内容
     * @return 路径为 {@code $} 的读取器
     * @throws DataAssertionException json 为空、为空白或不是合法 JSON 时抛出
     */
    public static @NotNull JsonReader parse(@Nullable String json) {
        if (json == null || json.isBlank()) {
            throw new DataAssertionException(ROOT_PATH, "JSON 内容不能为空", null);
        }
        try {
            return of(PARSER.readTree(json));
        } catch (JsonProcessingException exception) {
            throw new DataAssertionException(ROOT_PATH, "JSON 格式不合法", null, exception);
        }
    }

    /**
     * 把若干数据键的名称合并为字段名列表，用于和其他字段名一起传给 {@link #allowOnly(Collection)}。
     *
     * @param keys 允许出现的数据键
     * @return 按参数顺序排列的字段名
     */
    public static @NotNull List<String> names(@NotNull DataKey<?>... keys) {
        return Arrays.stream(keys).map(DataKey::name).toList();
    }

    /**
     * 当前位置在 JSON 中的完整路径，用于拼装错误信息。
     *
     * @return 根为 {@code $}，子字段为 {@code a.b}，数组元素为 {@code a[0]}
     */
    public @NotNull String path() {
        return path;
    }

    /**
     * 判断当前位置是否是 JSON 对象。
     *
     * @return 是对象时为 {@code true}；缺失、{@code null}、数组和标量都返回 {@code false}
     */
    public boolean isObject() {
        return node != null && node.isObject();
    }

    /**
     * 判断当前位置是否是 JSON 数组。
     *
     * @return 是数组时为 {@code true}
     */
    public boolean isArray() {
        return node != null && node.isArray();
    }

    /**
     * 读取必填字段。
     *
     * @param key 字段名和值类型
     * @param <T> 值类型
     * @return 字段值
     * @throws DataAssertionException 当前位置不是对象、字段缺失、为 {@code null} 或类型不符时抛出
     */
    public <T> @NotNull T required(@NotNull DataKey<T> key) {
        return child(key).value(key);
    }

    /**
     * 读取可选字段。
     *
     * @param key 字段名和值类型
     * @param <T> 值类型
     * @return 字段值；字段缺失或为 JSON {@code null} 时为空
     * @throws DataAssertionException 当前位置不是对象，或字段存在但类型不符时抛出
     */
    public <T> @NotNull Optional<T> optional(@NotNull DataKey<T> key) {
        return child(key).optionalValue(key);
    }

    /**
     * 把当前节点本身读成指定类型，用于 Map 的值或没有字段名的裸标量。
     * 数据键的名称不参与读取，只提供类型。
     *
     * @param key 提供值类型的数据键
     * @param <T> 值类型
     * @return 节点值
     * @throws DataAssertionException 节点缺失、为 {@code null} 或类型不符时抛出
     */
    public <T> @NotNull T value(@NotNull DataKey<T> key) {
        checkSupported(key.type());
        if (isAbsent()) {
            throw new DataAssertionException(path, "缺少必填内容", null);
        }
        return key.type().cast(read(Objects.requireNonNull(node), key.type(), path));
    }

    /**
     * 与 {@link #value(DataKey)} 相同，但节点缺失或为 {@code null} 时返回空。
     *
     * @param key 提供值类型的数据键
     * @param <T> 值类型
     * @return 节点值；缺失或为 JSON {@code null} 时为空
     * @throws DataAssertionException 节点存在但类型不符时抛出
     */
    public <T> @NotNull Optional<T> optionalValue(@NotNull DataKey<T> key) {
        checkSupported(key.type());
        if (isAbsent()) {
            return Optional.empty();
        }
        return Optional.of(key.type().cast(read(Objects.requireNonNull(node), key.type(), path)));
    }

    /**
     * 要求当前对象只含指定数据键的字段。
     *
     * @param keys 允许出现的数据键
     * @return 当前读取器，便于链式调用
     * @throws DataAssertionException 当前位置不是对象或出现其他字段时抛出，路径指向多余的字段
     */
    public @NotNull JsonReader allowOnly(@NotNull DataKey<?>... keys) {
        return allowOnly(names(keys));
    }

    /**
     * 要求当前对象只含指定名称的字段。
     *
     * @param names 允许出现的字段名
     * @return 当前读取器，便于链式调用
     * @throws DataAssertionException 当前位置不是对象或出现其他字段时抛出，路径指向多余的字段
     */
    public @NotNull JsonReader allowOnly(@NotNull Collection<String> names) {
        Set<String> allowed = new LinkedHashSet<>(names);
        requireObject().fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) {
                throw new DataAssertionException(childPath(name), "不允许出现该字段", null);
            }
        });
        return this;
    }

    /**
     * 读取必填的子对象。
     *
     * @param name 字段名
     * @return 指向该子对象的读取器
     * @throws DataAssertionException 当前位置不是对象，或字段缺失、为 {@code null}、不是对象时抛出
     */
    public @NotNull JsonReader object(@NotNull String name) {
        return optionalObject(name).orElseThrow(
                () -> new DataAssertionException(childPath(name), "缺少必填内容", null));
    }

    /**
     * 读取可选的子对象。
     *
     * @param name 字段名
     * @return 指向该子对象的读取器；字段缺失或为 JSON {@code null} 时为空
     * @throws DataAssertionException 当前位置不是对象，或字段存在但不是对象时抛出
     */
    public @NotNull Optional<JsonReader> optionalObject(@NotNull String name) {
        JsonReader child = field(name);
        if (child.isAbsent()) {
            return Optional.empty();
        }
        child.requireObject();
        return Optional.of(child);
    }

    /**
     * 读取必填的数组字段，每个元素对应一个读取器。
     *
     * @param name 字段名
     * @return 按原顺序排列、不可修改的元素读取器，元素路径形如 {@code name[0]}
     * @throws DataAssertionException 当前位置不是对象，或字段缺失、为 {@code null}、不是数组时抛出
     */
    public @NotNull List<JsonReader> array(@NotNull String name) {
        return optionalArray(name).orElseThrow(
                () -> new DataAssertionException(childPath(name), "缺少必填内容", null));
    }

    /**
     * 读取可选的数组字段。
     *
     * @param name 字段名
     * @return 元素读取器；字段缺失或为 JSON {@code null} 时为空
     * @throws DataAssertionException 当前位置不是对象，或字段存在但不是数组时抛出
     */
    public @NotNull Optional<List<JsonReader>> optionalArray(@NotNull String name) {
        JsonReader child = field(name);
        if (child.isAbsent()) {
            return Optional.empty();
        }
        return Optional.of(child.elements());
    }

    /**
     * 把当前对象的全部字段作为键由调用方决定的映射读取，例如语言标签或字段编码。
     *
     * @return 按 JSON 中出现顺序排列、不可修改的字段名到读取器的映射
     * @throws DataAssertionException 当前位置不是对象时抛出
     */
    public @NotNull Map<String, JsonReader> entries() {
        JsonNode object = requireObject();
        Map<String, JsonReader> result = new LinkedHashMap<>();
        object.fieldNames()
                .forEachRemaining(name -> result.put(name, new JsonReader(object.get(name), childPath(name))));
        return Collections.unmodifiableMap(result);
    }

    /**
     * 取得当前位置的 JSON 节点副本。
     *
     * <p>仅供仍以 {@code JsonNode} 为输入的嵌套 Codec 使用，调用后的读取不再受本读取器的严格规则和
     * 路径信息保护。能用本读取器表达的结构不应使用此方法。
     *
     * @return 节点的深拷贝
     * @throws DataAssertionException 节点缺失时抛出
     */
    public @NotNull JsonNode node() {
        if (node == null) {
            throw new DataAssertionException(path, "缺少必填内容", null);
        }
        return node.deepCopy();
    }

    /** 判断类型能否被读取器和 {@link JsonWriter} 处理。 */
    static boolean supports(@NotNull Class<?> type) {
        Class<?> single = type.isArray() ? type.getComponentType() : type;
        return single == String.class
                || single == Integer.class
                || single == Long.class
                || single == Boolean.class
                || single == BigDecimal.class
                || single.isEnum();
    }

    private static void checkSupported(Class<?> type) {
        if (!supports(type)) {
            throw new IllegalArgumentException("数据键类型不支持 JSON 读写: " + type.getName());
        }
    }

    private boolean isAbsent() {
        return node == null || node.isNull();
    }

    private JsonReader child(DataKey<?> key) {
        return field(key.name());
    }

    private JsonReader field(String name) {
        JsonNode object = requireObject();
        return new JsonReader(object.get(name), childPath(name));
    }

    private String childPath(String name) {
        return ROOT_PATH.equals(path) ? name : path + "." + name;
    }

    private JsonNode requireObject() {
        if (node == null || !node.isObject()) {
            throw new DataAssertionException(path, "必须是 JSON 对象", null);
        }
        return node;
    }

    private List<JsonReader> elements() {
        if (node == null || !node.isArray()) {
            throw new DataAssertionException(path, "必须是 JSON 数组", null);
        }
        List<JsonReader> result = new ArrayList<>(node.size());
        for (int index = 0; index < node.size(); index++) {
            result.add(new JsonReader(node.get(index), path + "[" + index + "]"));
        }
        return List.copyOf(result);
    }

    /** 按类型读取一个非空节点；数组类型逐个元素读取，元素为 {@code null} 视为失败。 */
    private static Object read(JsonNode node, Class<?> type, String path) {
        if (!type.isArray()) {
            return scalar(node, type, path);
        }
        if (!node.isArray()) {
            throw new DataAssertionException(path, "必须是 JSON 数组", null);
        }
        Class<?> componentType = type.getComponentType();
        Object result = Array.newInstance(componentType, node.size());
        for (int index = 0; index < node.size(); index++) {
            JsonNode element = node.get(index);
            String elementPath = path + "[" + index + "]";
            if (element.isNull()) {
                throw new DataAssertionException(elementPath, "数组元素不能为 null", null);
            }
            Array.set(result, index, scalar(element, componentType, elementPath));
        }
        return result;
    }

    private static Object scalar(JsonNode node, Class<?> type, String path) {
        if (type == String.class) {
            if (!node.isTextual() || node.textValue().isBlank()) {
                throw new DataAssertionException(path, "必须是非空文本", null);
            }
            return node.textValue();
        }
        if (type == Boolean.class) {
            if (!node.isBoolean()) {
                throw new DataAssertionException(path, "必须是 boolean", null);
            }
            return node.booleanValue();
        }
        if (type == Integer.class) {
            if (!node.isIntegralNumber() || !node.canConvertToInt()) {
                throw new DataAssertionException(path, "必须是 int 范围内的整数", null);
            }
            return node.intValue();
        }
        if (type == Long.class) {
            if (!node.isIntegralNumber() || !node.canConvertToLong()) {
                throw new DataAssertionException(path, "必须是 long 范围内的整数", null);
            }
            return node.longValue();
        }
        if (type == BigDecimal.class) {
            if (node.isNumber()) {
                return node.decimalValue();
            }
            if (node.isTextual()) {
                try {
                    return new BigDecimal(node.textValue().strip());
                } catch (NumberFormatException exception) {
                    throw new DataAssertionException(path, "必须是数值或数字文本", null, exception);
                }
            }
            throw new DataAssertionException(path, "必须是数值或数字文本", null);
        }
        Object[] constants = type.getEnumConstants();
        if (node.isTextual()) {
            for (Object constant : constants) {
                if (((Enum<?>) constant).name().equals(node.textValue())) {
                    return constant;
                }
            }
        }
        String allowed = Arrays.stream(constants)
                .map(constant -> ((Enum<?>) constant).name())
                .collect(Collectors.joining(", "));
        throw new DataAssertionException(path, "必须是枚举常量名之一: " + allowed, null);
    }
}
