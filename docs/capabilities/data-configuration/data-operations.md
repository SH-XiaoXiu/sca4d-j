# DataOps

源码：`core/sca4dj-core-api/src/main/java/com/sca4d/core/api/data/DataOps.java`

本页说明 DataOps 的公开入口、字节和 JSON 规则，以及失败与数据安全要求。本页适用于在业务代码中处理文本、数值、字节协议和 JSON 的开发者。读完后，开发者能判断一段数据处理应使用哪个入口，以及失败如何报告。

## 能处理哪些数据

DataOps 是无框架的数据处理工具。它接收调用方已经取得的值，在内存中完成文本、数值、集合、字节和 JSON 操作，并返回新的处理对象或最终值。Repository 查询、事务、HTTP 绑定和任务调度，由调用 DataOps 的代码负责。

JSON 入口接收 String 或 `byte[]`。读取结果使用普通 Java 标量、Map 和 List，输出为 String 或独立的 `byte[]`。Jackson Databind 执行内部解析和序列化。

JSON、集合和字节数组在进入处理器时复制。后续操作都基于创建时的快照。

## 公开入口

|                         入口                          |                         返回结果                          |
|-----------------------------------------------------|-------------------------------------------------------|
| `s(value)`                                          | TextPipe，处理可空文本                                      |
| `v(value)`                                          | ValuePipe，映射、转换并断言普通值                                |
| `j(json)`                                           | JsonPipe，严格解析和编辑 JSON                               |
| `json(bytes)`                                       | JsonPipe，严格解析 JSON 字节                                 |
| `seq(values)`                                       | SeqPipe，保存不可修改的集合快照                                 |
| `bytes(values)`                                     | BytePipe，按偏移读取字节并计算校验值                              |
| `bytes(length)`                                     | ByteBuilder，在固定长度数组中按偏移写入数值                         |
| `obj()`                                             | JsonObjectPipe，不可变地构建 JSON 对象                         |
| `id()`、`id(prefix)`                                | IdPipe，生成默认 12 位或指定正长度随机 ID                         |
| `add(left, right)`                                  | 对可空 Long 求和；都为空时返回 `null`                             |
| `nil(values)`                                       | 判断集合为 `null` 或空                                      |
| `summary(stageCode, detailJson)`                    | 创建带 `stageCode` 和可选 `detail` 的 JSON 对象                  |
| `resourceUrl(baseUrl, path, baseField, pathField)` | 保留绝对 HTTP/HTTPS URL，否则规范连接基础 URL 与相对路径                |

TextPipe 提供 `strip`、`lower`、`upper`、`cut`、`nil`、`or`、`req`、`has`、`get` 和 `opt`。ValuePipe 提供 `map`、`str`、`dec`、`lng`、`bool`、`req`、`ok`、`min`、`as`、`or` 和 `get`。

所有转换返回新的 pipe。`req`、`ok`、`min` 和 `as` 失败时，抛出带 `field` 的 [DataAssertionException](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/data/DataAssertionException.html)。

SeqPipe 提供 `req`、`map`、`filter`、`list` 和 `size`。构造函数立即复制集合，`list()` 返回不可修改的列表。

JsonObjectPipe 的 `put` 和 `opt` 序列化普通值。`json` 忽略空白输入，并严格解析非空 JSON。`req` 要求对象至少含一个字段。

所有转换返回新的 pipe，同一个 pipe 可以安全地分叉成多个后续处理。

## 字节规则

BytePipe 保存输入数组快照。`requireSize(...)` 和 `requireSizeBetween(...)` 校验完整帧长度。`unsignedByte(...)`、`readShort(...)`、`readUnsignedShort(...)`、`readInt(...)`、`readUnsignedInt(...)` 和 `readLong(...)` 按明确偏移读取数值。多字节数值必须同时传入 ByteOrder。

`slice(...)` 复制指定范围。`text(...)` 使用严格字符解码。`hex()` 返回小写十六进制文本。`xor8(...)`、`sum8(...)`、`crc32(...)` 和 `crc32c(...)` 只计算调用方指定的半开字节范围。

负偏移、负长度、整数溢出和越界，统一抛出 DataAssertionException。异常使用调用方传入的 `field` 定位协议字段。

ByteBuilder 在创建时确定输出长度。写入方法使用与 BytePipe 相同的偏移和 ByteOrder 规则。无符号 byte 和 short 同时检查数值范围。`sum8(...)` 可在构建过程中计算校验值。`get()` 返回当前构建结果的独立副本。

```java
DataOps.BytePipe frame = DataOps.bytes(payload)
        .requireSize(14, "vendor-frame");
long messageId = frame.readLong(3, ByteOrder.LITTLE_ENDIAN, "messageId");

byte[] response = DataOps.bytes(4)
        .writeUnsignedShort(0, 512, ByteOrder.BIG_ENDIAN, "length")
        .writeUnsignedByte(2, 1, "status")
        .writeUnsignedByte(3, 0, "checksum")
        .get();
```

## JSON 规则

`j(null)`、`j(blank)`、`json(null)` 和 `json(new byte[0])` 表示没有 JSON。此时，`get()` 和 `bytes()` 返回 `null`。

只有显式调用 `orObj()` 才创建空对象。已有数组或标量不能通过 `orObj()` 被替换为对象。

非空但语法错误的输入，在解析入口立即抛出 DataAssertionException。

JsonPipe 的基础读取与合并方法包括：

- `req(field)` 要求当前值是 JSON 对象；
- `text(field)` 和 `dec(field)` 读取顶层字段；
- `requiredObject(path)`、`requiredText(path)`、`requiredBoolean(path)`、`requiredDecimal(path)`、`requiredInteger(path)` 和 `requiredLong(path)` 按点分路径执行严格类型读取；
- `objectValues()` 把当前对象复制为字段顺序稳定且不可修改的普通 Java Map、List 和标量；
- `as(type, field)` 把当前对象映射为 Bean，映射失败时不返回空值；
- `pub(fields)` 保护已有顶层字段，后续 patch 改变字段时失败；
- `merge(patchJson)` 递归合并两个对象。嵌套对象继续合并，其他节点由 patch 覆盖；
- `get()` 返回当前 JSON 文本，`bytes()` 返回独立的 JSON 字节，`orObj()` 只为空输入创建对象。

JsonPipe 还使用点分路径执行 `put`、`putJson`、`remove`、`rename`、`edit` 和 `list`。写入与子对象编辑可以创建缺失的中间对象。已有中间节点不是对象时失败。

删除、重命名和列表编辑遇到目标缺失时，保持结果不变。目标存在但类型不符时，则失败。`list` 只替换数组中的对象元素，标量和 `null` 保持原值。

重命名先在新 JSON 副本中完成目标写入，再删除来源。目标路径写入失败时，方法抛出异常，原 JsonPipe 和来源字段都保持不变。来源和目标路径不得互为祖先或后代。点分路径的每一段都必须是非空字段名。非法路径和相互包含的重命名路径，抛出 IllegalArgumentException。

```java
String json = DataOps.j("{\"profile\":{},\"items\":[{\"name\":\"A\"},2]}")
        .put("profile.enabled", true)
        .list("items", item -> item.rename("name", "label"))
        .merge("{\"profile\":{\"mode\":\"FULL\"}}")
        .get();
```

## 失败与数据安全

JSON 语法、类型、序列化和 Bean 映射失败，都抛出 DataAssertionException，并保留底层 cause。

DataAssertionException 默认只保存字段、失败原因和 cause。调用方只有在短文本已经脱敏时，才传入 `safeSample`。

DataOps 不替代 [DataGraphExecutor](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/data/DataGraphExecutor.html)。数据图的输入匹配、拓扑执行、节点可见输入和节点异常分类，只由 DataGraphExecutor 负责。

## 文本与集合示例

以下示例整理成员导入的一行数据。`displayName` 与 `emails` 是调用方已经取得的值：

```java
String name = DataOps.s(displayName)
        .strip()
        .req("displayName")
        .get();

List<String> normalized = DataOps.seq(emails)
        .map(email -> DataOps.s(email).strip().lower().get())
        .filter(email -> email != null && !email.isEmpty())
        .list();
```

`req` 在值为空时抛出带 `field` 的 DataAssertionException。`seq(...).list()` 返回不可修改的列表。
