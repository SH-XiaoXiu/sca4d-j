# JsonCodec 读写器

源码：`core/sca4dj-core-api/src/main/java/com/sca4d/core/api/codec/JsonReader.java`、`JsonWriter.java`

本页说明 `JsonReader` 和 `JsonWriter` 的读写规则与失败方式。适用于实现 `JsonCodec<V>`，需要把固定结构的 JSON 配置转换为领域对象的开发者。

## 与 DataOps 的分工

[DataOps](/capabilities/data-configuration/data-operations) 用于流水线阶段和 DataOperation 中的数据整理：先宽松地取值，再显式断言。`JsonReader` 和 `JsonWriter` 用于 `JsonCodec` 中结构封闭的配置：默认严格，不做类型转换，可以拒绝未知字段，错误带完整路径。DataOps 不增加面向 Codec 的读写能力。

## 读取

```java
JsonReader root = JsonReader.parse(json);
root.allowOnly(NAME, PRICE, LABELS);
String name = root.required(NAME);
BigDecimal price = root.optional(PRICE).orElse(BigDecimal.ZERO);
Map<String, JsonReader> labels = root.object("labels").entries();
```

- `required` 在字段缺失或为 JSON `null` 时失败，`optional` 返回空。
- 值类型支持 `String`（非空白）、`Integer`、`Long`、`Boolean`、`BigDecimal`、枚举和这些类型的数组。数字不会被读成文本，文本也不会被读成数字。`BigDecimal` 同时接受数字和数字文本。枚举按常量名精确匹配，失败信息列出允许的名称。
- 其他类型抛出 `IllegalArgumentException`。
- `value(key)` 把节点自身读成键的类型，用于 Map 的值和裸标量；`entries()` 按 JSON 顺序返回键由调用方决定的字段。
- `object`、`array` 及对应的 `optional` 方法返回子读取器，路径逐层扩展，如 `fields.PRICE.zh-CN.label`、`scopes[2].values[0]`。
- `allowOnly(...)` 拒绝未声明的字段，错误路径指向多余的字段。
- `node()` 返回当前节点的副本，仅供仍接收 `JsonNode` 的嵌套 Codec 使用。

失败抛出 `DataAssertionException`，`field` 为完整路径，根为 `$`，信息不含 JSON 中的实际值。

## 写入

```java
ObjectNode node = JsonWriter.object()
        .put(NAME, name)
        .opt(UNIT, unit)
        .put("labels", JsonWriter.object().entry("zh-CN", String.class, "价格"))
        .node();
```

字段按写入顺序排列。枚举写常量名；`BigDecimal` 写为 `toPlainString()` 的字符串以保留精度，读取器可以读回。`opt` 跳过 `null`。`JsonWriter.scalar(key, value)` 生成没有字段名的裸标量节点，如 `90`、`"X"`、`["G1"]`。重复字段、空白文本和不支持的类型是调用方代码错误，抛出 `IllegalArgumentException` 或 `IllegalStateException`。
