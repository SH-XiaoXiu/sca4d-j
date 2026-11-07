package com.sca4d.core.api.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.data.DataKey;
import com.sca4d.core.api.data.exception.DataAssertionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class JsonReaderWriterTest {

    enum Mode {
        FIXED,
        RANGE
    }

    private static final DataKey<String> NAME = DataKey.of("name", String.class);

    private static final DataKey<Integer> COUNT = DataKey.of("count", Integer.class);

    private static final DataKey<Long> BIG = DataKey.of("big", Long.class);

    private static final DataKey<Boolean> FLAG = DataKey.of("flag", Boolean.class);

    private static final DataKey<BigDecimal> PRICE = DataKey.of("price", BigDecimal.class);

    private static final DataKey<Mode> MODE = DataKey.of("mode", Mode.class);

    private static final DataKey<String[]> TAGS = DataKey.of("tags", String[].class);

    private static final DataKey<Mode[]> MODES = DataKey.of("modes", Mode[].class);

    private static final DataKey<JsonNode> RAW = DataKey.of("raw", JsonNode.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static DataAssertionException fails(Runnable action) {
        return assertThrows(DataAssertionException.class, action::run);
    }

    @Test
    @DisplayName("读取各类必填标量")
    void readsRequiredScalars() {
        JsonReader reader = JsonReader.parse("""
                {"name":"a","count":3,"big":9000000000,"flag":true,"price":1.50,"mode":"RANGE"}""");
        assertEquals("a", reader.required(NAME));
        assertEquals(3, reader.required(COUNT));
        assertEquals(9000000000L, reader.required(BIG));
        assertTrue(reader.required(FLAG));
        assertEquals(0, new BigDecimal("1.5").compareTo(reader.required(PRICE)));
        assertEquals(Mode.RANGE, reader.required(MODE));
    }

    @Test
    @DisplayName("必填字段缺失或为 null 时带路径失败，可选字段返回空")
    void missingAndNull() {
        JsonReader reader = JsonReader.parse("{\"name\":null}");
        assertEquals("name", fails(() -> reader.required(NAME)).field());
        assertEquals("count", fails(() -> reader.required(COUNT)).field());
        assertEquals(Optional.empty(), reader.optional(NAME));
        assertEquals(Optional.empty(), reader.optional(COUNT));
    }

    @Test
    @DisplayName("数字不会被读成文本，文本也不会被读成数字或布尔值")
    void rejectsCoercion() {
        JsonReader reader = JsonReader.parse("{\"name\":12,\"count\":\"3\",\"flag\":\"true\",\"big\":1.5}");
        assertEquals("name", fails(() -> reader.required(NAME)).field());
        assertEquals("count", fails(() -> reader.required(COUNT)).field());
        assertEquals("flag", fails(() -> reader.required(FLAG)).field());
        assertEquals("big", fails(() -> reader.required(BIG)).field());
    }

    @Test
    @DisplayName("空白文本被拒绝，且异常信息不含实际值")
    void rejectsBlankText() {
        JsonReader reader = JsonReader.parse("{\"name\":\"  \"}");
        DataAssertionException exception = fails(() -> reader.required(NAME));
        assertEquals("name", exception.field());
        assertFalse(exception.getMessage().contains(" \" "));
    }

    @Test
    @DisplayName("int 超出范围被拒绝")
    void rejectsIntOverflow() {
        JsonReader reader = JsonReader.parse("{\"count\":9000000000}");
        assertEquals("count", fails(() -> reader.required(COUNT)).field());
    }

    @Test
    @DisplayName("BigDecimal 接受数字和数字文本，拒绝非数字文本与布尔值")
    void bigDecimalNumberOrString() {
        assertEquals(new BigDecimal("12.50"), JsonReader.parse("{\"price\":\"12.50\"}").required(PRICE));
        assertEquals(0, new BigDecimal("12.5").compareTo(JsonReader.parse("{\"price\":12.5}").required(PRICE)));
        assertEquals("price", fails(() -> JsonReader.parse("{\"price\":\"abc\"}").required(PRICE)).field());
        assertEquals("price", fails(() -> JsonReader.parse("{\"price\":true}").required(PRICE)).field());
    }

    @Test
    @DisplayName("枚举按常量名精确匹配，未知名称的错误列出允许的名称")
    void enumNames() {
        assertEquals(Mode.FIXED, JsonReader.parse("{\"mode\":\"FIXED\"}").required(MODE));
        DataAssertionException exception = fails(() -> JsonReader.parse("{\"mode\":\"fixed\"}").required(MODE));
        assertEquals("mode", exception.field());
        assertTrue(exception.getMessage().contains("FIXED, RANGE"));
        assertFalse(exception.getMessage().contains("fixed"));
    }

    @Test
    @DisplayName("读取文本数组和枚举数组，元素错误带下标")
    void arrays() {
        JsonReader reader = JsonReader.parse("{\"tags\":[\"a\",\"b\"],\"modes\":[\"FIXED\",\"RANGE\"]}");
        assertArrayEquals(new String[] {"a", "b"}, reader.required(TAGS));
        assertArrayEquals(new Mode[] {Mode.FIXED, Mode.RANGE}, reader.required(MODES));

        assertEquals("tags[1]", fails(() -> JsonReader.parse("{\"tags\":[\"a\",2]}").required(TAGS)).field());
        assertEquals("tags[0]", fails(() -> JsonReader.parse("{\"tags\":[null]}").required(TAGS)).field());
        assertEquals(
                "modes[1]",
                fails(() -> JsonReader.parse("{\"modes\":[\"FIXED\",\"X\"]}").required(MODES)).field());
        assertEquals("tags", fails(() -> JsonReader.parse("{\"tags\":\"a\"}").required(TAGS)).field());
    }

    @Test
    @DisplayName("不支持的类型是调用方错误")
    void unsupportedType() {
        JsonReader reader = JsonReader.parse("{\"raw\":{}}");
        assertThrows(IllegalArgumentException.class, () -> reader.required(RAW));
        assertThrows(IllegalArgumentException.class, () -> reader.optional(RAW));
        assertThrows(IllegalArgumentException.class, () -> JsonWriter.object().put(RAW, MAPPER.createObjectNode()));
    }

    @Test
    @DisplayName("value 把节点本身读成键的类型")
    void valueReadsNode() {
        assertEquals(90, JsonReader.parse("90").value(COUNT));
        assertEquals("X", JsonReader.parse("\"X\"").value(NAME));
        assertArrayEquals(new String[] {"G1"}, JsonReader.parse("[\"G1\"]").value(TAGS));
        assertEquals("$", fails(() -> JsonReader.parse("null").value(NAME)).field());
        assertEquals(Optional.empty(), JsonReader.parse("null").optionalValue(NAME));
    }

    @Test
    @DisplayName("allowOnly 拒绝未声明字段并给出嵌套完整路径")
    void allowOnlyNestedPath() {
        JsonReader root = JsonReader
                .parse("{\"name\":\"a\",\"fields\":{\"PRICE\":{\"zh-CN\":{\"label\":\"x\",\"extra\":1}}}}");
        root.allowOnly(NAME, DataKey.of("fields", String.class));
        JsonReader label = root.object("fields").object("PRICE").object("zh-CN");
        label.allowOnly(DataKey.of("label", String.class), DataKey.of("extra", Integer.class));
        assertEquals(
                "fields.PRICE.zh-CN.extra",
                fails(() -> label.allowOnly(DataKey.of("label", String.class))).field());
        assertEquals("other", fails(() -> JsonReader.parse("{\"other\":1}").allowOnly(NAME)).field());
        List<String> names = JsonReader.names(NAME, COUNT);
        assertEquals("zh", fails(() -> JsonReader.parse("{\"zh\":1}").allowOnly(names)).field());
    }

    @Test
    @DisplayName("子对象和数组的路径逐层扩展")
    void childPaths() {
        JsonReader root = JsonReader.parse("{\"a\":{\"items\":[{\"name\":1}]}}");
        JsonReader a = root.object("a");
        assertEquals("$", root.path());
        assertEquals("a", a.path());
        List<JsonReader> items = a.array("items");
        assertEquals("a.items[0]", items.get(0).path());
        assertEquals("a.items[0].name", fails(() -> items.get(0).required(NAME)).field());
        assertEquals("a.missing", fails(() -> a.object("missing")).field());
        assertEquals("a.missing", fails(() -> a.array("missing")).field());
        assertEquals(Optional.empty(), a.optionalObject("missing"));
        assertEquals(Optional.empty(), a.optionalArray("missing"));
        assertEquals("a.items", fails(() -> a.object("items")).field());
        assertEquals("a", fails(() -> root.array("a")).field());
    }

    @Test
    @DisplayName("entries 保持 JSON 顺序并带键路径")
    void entriesKeepOrder() {
        JsonReader reader = JsonReader.parse("{\"zh-CN\":{\"label\":\"a\"},\"en\":{\"label\":\"b\"},\"km\":{}}");
        Map<String, JsonReader> entries = reader.entries();
        assertEquals(List.of("zh-CN", "en", "km"), List.copyOf(entries.keySet()));
        assertEquals("zh-CN", entries.get("zh-CN").path());
        assertEquals("km.name", fails(() -> entries.get("km").required(NAME)).field());
        assertThrows(UnsupportedOperationException.class, () -> entries.put("x", reader));
        assertEquals("$", fails(() -> JsonReader.parse("[1]").entries()).field());
    }

    @Test
    @DisplayName("null、非法 JSON 和尾随内容在读取时失败")
    void invalidInput() {
        assertEquals("$", fails(() -> JsonReader.of(null).required(NAME)).field());
        assertEquals("$", fails(() -> JsonReader.parse(" ")).field());
        assertEquals("$", fails(() -> JsonReader.parse("{")).field());
        assertEquals("$", fails(() -> JsonReader.parse("{} x")).field());
        assertEquals("$", fails(() -> JsonReader.of(null).node()).field());
        assertTrue(JsonReader.parse("{}").isObject());
        assertTrue(JsonReader.parse("[]").isArray());
        assertFalse(JsonReader.of(null).isObject());
    }

    @Test
    @DisplayName("node 返回副本，修改不影响读取器")
    void nodeIsCopy() {
        JsonReader reader = JsonReader.parse("{\"name\":\"a\"}");
        ((com.fasterxml.jackson.databind.node.ObjectNode) reader.node()).put("name", "b");
        assertEquals("a", reader.required(NAME));
    }

    @Test
    @DisplayName("写入后读回得到相同的值，字段保持写入顺序")
    void writerRoundTrip() {
        JsonNode node = JsonWriter.object()
                .put(NAME, "a")
                .put(COUNT, 3)
                .put(BIG, 9000000000L)
                .put(FLAG, false)
                .put(PRICE, new BigDecimal("1E+3"))
                .put(MODE, Mode.RANGE)
                .put(TAGS, new String[] {"x", "y"})
                .put(MODES, new Mode[] {Mode.FIXED})
                .opt(DataKey.of("skipped", String.class), null)
                .node();
        assertEquals(
                List.of("name", "count", "big", "flag", "price", "mode", "tags", "modes"),
                List.copyOf(node.properties().stream().map(Map.Entry::getKey).toList()));
        assertEquals("1000", node.get("price").textValue());
        assertEquals("RANGE", node.get("mode").textValue());

        JsonReader reader = JsonReader.of(node);
        assertEquals("a", reader.required(NAME));
        assertEquals(3, reader.required(COUNT));
        assertEquals(9000000000L, reader.required(BIG));
        assertFalse(reader.required(FLAG));
        assertEquals(0, new BigDecimal("1000").compareTo(reader.required(PRICE)));
        assertEquals(Mode.RANGE, reader.required(MODE));
        assertArrayEquals(new String[] {"x", "y"}, reader.required(TAGS));
        assertArrayEquals(new Mode[] {Mode.FIXED}, reader.required(MODES));
        assertEquals(Optional.empty(), reader.optional(DataKey.of("skipped", String.class)));
    }

    @Test
    @DisplayName("opt 写入非空值")
    void optWritesValue() {
        assertEquals("a", JsonWriter.object().opt(NAME, "a").node().get("name").textValue());
    }

    @Test
    @DisplayName("子对象、对象数组、动态键和原始节点写入后可读回")
    void writerNesting() {
        JsonNode node = JsonWriter.object()
                .put(
                        "labels",
                        JsonWriter.object()
                                .put("zh-CN", JsonWriter.object().put(NAME, "价格"))
                                .entry("en", String.class, "Price"))
                .put("items", List.of(JsonWriter.object().put(COUNT, 1), JsonWriter.object().put(COUNT, 2)))
                .put("raw", MAPPER.createObjectNode().put("k", 1))
                .node();
        JsonReader reader = JsonReader.of(node);
        Map<String, JsonReader> labels = reader.object("labels").entries();
        assertEquals(List.of("zh-CN", "en"), List.copyOf(labels.keySet()));
        assertEquals("价格", labels.get("zh-CN").required(NAME));
        assertEquals("Price", labels.get("en").value(NAME));
        assertEquals(2, reader.array("items").get(1).required(COUNT));
        assertEquals(1, reader.object("raw").node().get("k").intValue());
    }

    @Test
    @DisplayName("写入子对象时复制内容")
    void writerCopiesChild() {
        JsonWriter child = JsonWriter.object().put(NAME, "a");
        JsonWriter parent = JsonWriter.object().put("child", child);
        child.put(COUNT, 1);
        assertFalse(parent.node().get("child").has("count"));
    }

    @Test
    @DisplayName("scalar 生成裸标量节点")
    void scalarNodes() {
        assertEquals("90", JsonWriter.scalar(COUNT, 90).toString());
        assertEquals("\"X\"", JsonWriter.scalar(NAME, "X").toString());
        assertEquals("[\"G1\"]", JsonWriter.scalar(TAGS, new String[] {"G1"}).toString());
        assertEquals("\"1.50\"", JsonWriter.scalar(PRICE, new BigDecimal("1.50")).toString());
        assertEquals("[{}]", JsonWriter.array(List.of(JsonWriter.object())).toString());
        assertEquals(90, JsonReader.of(JsonWriter.scalar(COUNT, 90)).value(COUNT));
    }

    @Test
    @DisplayName("写入非法输入是调用方错误")
    void writerRejectsInvalid() {
        assertThrows(IllegalArgumentException.class, () -> JsonWriter.object().put(NAME, " "));
        assertThrows(IllegalArgumentException.class, () -> JsonWriter.object().put(NAME, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> JsonWriter.object().put(TAGS, new String[] {"a", null}));
        assertThrows(IllegalStateException.class, () -> JsonWriter.object().put(NAME, "a").put(NAME, "b"));
        assertThrows(
                IllegalStateException.class,
                () -> JsonWriter.object().put(NAME, "a").put("name", JsonWriter.object()));
    }
}
