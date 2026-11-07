package com.sca4d.core.api.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.core.api.data.DataKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Objects;

/**
 * 按 {@link DataKey} 构建 JSON 对象，是 {@link JsonReader} 的写入侧，供 {@link JsonCodec#encode} 使用。
 *
 * <p>字段按 {@code put} 的调用顺序写入。写入规则与读取器一一对应：
 * 文本、整数和布尔值按原样写入，枚举写为常量名，数组写为元素数组；
 * {@link BigDecimal} 写成 {@link BigDecimal#toPlainString()} 的字符串，避免 JSON 数字经过
 * double 的中间环节而丢失精度，{@link JsonReader} 同时接受数字和数字文本，因此可以读回。
 *
 * <p>同一对象内重复写入同一个字段名、写入空白文本、写入 {@code null}（{@code opt} 除外）和使用不支持的类型，
 * 都是调用方代码错误，抛出 {@link IllegalArgumentException} 或 {@link IllegalStateException}。
 * 写入子对象和数组时复制其内容，之后再修改子写入器不影响已写入的内容。
 *
 * <pre>{@code
 * ObjectNode node = JsonWriter.object()
 *         .put(NAME, "price")
 *         .opt(UNIT, unit)
 *         .put("labels", JsonWriter.object().entry("zh-CN", String.class, "价格"))
 *         .node();
 * }</pre>
 */
public final class JsonWriter {

    private final ObjectNode node = JsonNodeFactory.instance.objectNode();

    private JsonWriter() {
    }

    /**
     * 创建空对象写入器。
     *
     * @return 新写入器
     */
    public static @NotNull JsonWriter object() {
        return new JsonWriter();
    }

    /**
     * 把若干对象写入器的内容按顺序组成 JSON 数组节点。
     *
     * @param children 数组元素，内容在调用时复制
     * @return 新数组节点
     */
    public static @NotNull ArrayNode array(@NotNull Collection<JsonWriter> children) {
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        children.forEach(child -> array.add(child.node.deepCopy()));
        return array;
    }

    /**
     * 把单个值转换为裸 JSON 节点，用于没有字段名的标量，如 {@code 90}、{@code "X"}、{@code ["G1"]}。
     *
     * @param key 提供值类型的数据键，名称不参与写入
     * @param value 值；不能为空
     * @param <T> 值类型
     * @return 对应的 JSON 节点
     * @throws IllegalArgumentException 值为空、为空白文本、数组含空元素或类型不支持时抛出
     */
    public static <T> @NotNull JsonNode scalar(@NotNull DataKey<T> key, @NotNull T value) {
        return toNode(key.type(), value);
    }

    /**
     * 写入必填字段。
     *
     * @param key 字段名和值类型
     * @param value 字段值
     * @param <T> 值类型
     * @return 当前写入器
     * @throws IllegalArgumentException 值为空、为空白文本、数组含空元素或类型不支持时抛出
     * @throws IllegalStateException 字段名已经写入时抛出
     */
    public <T> @NotNull JsonWriter put(@NotNull DataKey<T> key, @NotNull T value) {
        return add(key.name(), toNode(key.type(), value));
    }

    /**
     * 写入可选字段，值为 {@code null} 时跳过，不写 JSON {@code null}。
     *
     * @param key 字段名和值类型
     * @param value 字段值；可为空
     * @param <T> 值类型
     * @return 当前写入器
     */
    public <T> @NotNull JsonWriter opt(@NotNull DataKey<T> key, @Nullable T value) {
        return value == null ? this : put(key, value);
    }

    /**
     * 以调用方决定的字段名写入标量，用于语言标签、字段编码等动态键。
     *
     * @param name 字段名
     * @param type 值类型
     * @param value 字段值
     * @param <T> 值类型
     * @return 当前写入器
     * @throws IllegalArgumentException 值为空、为空白文本、数组含空元素或类型不支持时抛出
     * @throws IllegalStateException 字段名已经写入时抛出
     */
    public <T> @NotNull JsonWriter entry(@NotNull String name, @NotNull Class<T> type, @NotNull T value) {
        return add(name, toNode(type, value));
    }

    /**
     * 写入子对象。
     *
     * @param name 字段名，可以是动态键
     * @param child 子对象写入器，内容在调用时复制
     * @return 当前写入器
     * @throws IllegalStateException 字段名已经写入时抛出
     */
    public @NotNull JsonWriter put(@NotNull String name, @NotNull JsonWriter child) {
        return add(name, child.node.deepCopy());
    }

    /**
     * 写入对象数组。
     *
     * @param name 字段名
     * @param children 数组元素，内容在调用时复制
     * @return 当前写入器
     * @throws IllegalStateException 字段名已经写入时抛出
     */
    public @NotNull JsonWriter put(@NotNull String name, @NotNull Collection<JsonWriter> children) {
        return add(name, array(children));
    }

    /**
     * 写入已经构造好的 JSON 节点。
     *
     * <p>仅供仍返回 {@code JsonNode} 的嵌套 Codec 使用。节点内容不经过本类的类型检查。
     *
     * @param name 字段名
     * @param raw 节点，内容在调用时复制
     * @return 当前写入器
     * @throws IllegalStateException 字段名已经写入时抛出
     */
    public @NotNull JsonWriter put(@NotNull String name, @NotNull JsonNode raw) {
        return add(name, raw.deepCopy());
    }

    /**
     * 取得已写入内容的副本。
     *
     * @return 保持写入顺序的新对象节点
     */
    public @NotNull ObjectNode node() {
        return node.deepCopy();
    }

    private JsonWriter add(String name, JsonNode value) {
        Objects.requireNonNull(name, "字段名不能为空");
        if (node.has(name)) {
            throw new IllegalStateException("JSON 字段重复写入: " + name);
        }
        node.set(name, value);
        return this;
    }

    private static JsonNode toNode(Class<?> type, Object value) {
        if (!JsonReader.supports(type)) {
            throw new IllegalArgumentException("数据键类型不支持 JSON 读写: " + type.getName());
        }
        if (value == null) {
            throw new IllegalArgumentException("写入值不能为空，可选字段请使用 opt");
        }
        if (type.isArray()) {
            ArrayNode array = JsonNodeFactory.instance.arrayNode();
            for (int index = 0; index < Array.getLength(value); index++) {
                array.add(toNode(type.getComponentType(), Array.get(value, index)));
            }
            return array;
        }
        if (value instanceof String text) {
            if (text.isBlank()) {
                throw new IllegalArgumentException("不能写入空白文本");
            }
            return JsonNodeFactory.instance.textNode(text);
        }
        if (value instanceof Integer number) {
            return JsonNodeFactory.instance.numberNode(number);
        }
        if (value instanceof Long number) {
            return JsonNodeFactory.instance.numberNode(number);
        }
        if (value instanceof Boolean flag) {
            return JsonNodeFactory.instance.booleanNode(flag);
        }
        if (value instanceof BigDecimal decimal) {
            return JsonNodeFactory.instance.textNode(decimal.toPlainString());
        }
        return JsonNodeFactory.instance.textNode(((Enum<?>) value).name());
    }
}
