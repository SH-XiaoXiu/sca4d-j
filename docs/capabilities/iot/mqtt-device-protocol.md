# MqttDeviceProtocol

源码：`modules/iot/sca4dj-iot-emqx/src/main/java/com/sca4d/iot/emqx/protocol/MqttDeviceProtocol.java`

## 处理对象

MqttDeviceProtocol 使用一个稳定 code 保存 MQTT 上行路由、下行路由、Core Pipeline 和物模型 DataKey 映射。具体应用创建并登记协议。MqttProtocolCatalog 按 code 查找 Device 当前绑定的协议。

协议包含三类定义：

| 定义 | Pipeline 输入 | Pipeline 输出 | 业务结果 |
|---|---|---|---|
| reported 上行 | `byte[]` MQTT payload | DataValues | ReportedStateUpdate |
| ACK 上行 | `byte[]` MQTT payload | DecodedMqttAcknowledgement | DeviceAcknowledgement |
| desired 或 command 下行 | MqttDownlinkFrame | `byte[]` 厂商 payload | EncodedMqttMessage |

Topic、字段名、厂商 tag、字节序、校验范围和固件兼容规则写在 Pipeline 阶段中。MqttDeviceProtocol 负责选择 Pipeline、准备 DataValues、限制 reported 字段范围和验证物模型映射。Pipeline 由 Core 提供的线性阶段组成，每个阶段的输入与输出类型在编译期确定，阶段名称用于定位失败位置。

## 创建协议

`builder(code)` 创建协议构建器。`reported(...)` 登记 Topic Filter、字段组 code、`ReportedMergeMode`、`Pipeline<byte[], DataValues>` 和允许映射的 DataKey。`acknowledgement(...)` 登记 Topic Filter 与 `Pipeline<byte[], DecodedMqttAcknowledgement>`。`downlink(...)` 登记 desired 属性或 command 路由、`Pipeline<MqttDownlinkFrame, byte[]>` 和下行输入 DataKey。

```java
DataKey<Boolean> power = DataKey.of("power", Boolean.class);

Pipeline<byte[], DataValues> reported = Pipeline
        .of(PipelineStage.of(
                "parse-reported-json",
                byte[].class,
                DataOps.JsonPipe.class,
                payload -> DataOps.json(payload).req("reported")))
        .then(PipelineStage.of(
                "map-reported-fields",
                DataOps.JsonPipe.class,
                DataValues.class,
                json -> DataValues.builder()
                        .put(power, json.requiredBoolean("properties.power"))
                        .build()));

MqttDeviceProtocol protocol = MqttDeviceProtocol.builder("demo-json-v1")
        .reported(
                MqttUplinkRoute.topic("demo/+/reported"),
                "reported",
                ReportedMergeMode.SNAPSHOT,
                reported,
                power)
        .build();
```

Builder 拒绝重复上行 Topic Filter（`MQTT uplink Topic Filter 重复`）、重复的下行 kind 与 target code（`MQTT downlink route 重复`）、重复 DataKey 和没有任何路由的协议。每条 reported 和下行定义至少声明一个 DataKey。

## Topic 路由

`MqttUplinkRoute.topic(filter)` 把 Topic Filter 中唯一的 `+` 层解释为 Device MQTT clientId。Filter 含多个 `+` 时，调用 `topic(filter, identityLevel)` 明确对应层。协议 Filter 从真实业务 Topic 开始。EmqxMqttRuntime 在订阅时添加共享订阅前缀 `$share/<group>/`。

MqttDeviceProtocol 的 `uplinkTopicFilters()` 返回全部上行 Topic Filter。`clientId(topicIdentity)` 从 Topic 身份还原 MQTT Client ID。`deviceUplinkTopicFilters(clientId)` 与 `deviceDownlinkTopics(clientId)` 分别返回指定 Client 的上行 Filter 和下行 Topic。

`MqttDownlinkRoute.desired(...)` 和 `command(...)` 按 kind 与 target code 选择 encoder。Topic 模板必须包含 `{identity}`，生成精确 Topic 时以 Device MQTT clientId 替换模板中的占位符。

## reported 与 ACK

MqttUplinkDefinition 在执行 Pipeline 前复制 payload。reported Pipeline 返回 DataValues 后，定义只读取登记的 DataKey：

- `coveredProperties` 包含 MQTT 路由登记的全部属性 code。
- `values` 只包含本次 DataValues 实际提供的键。
- SNAPSHOT 路由通常要求协议阶段产出完整字段。
- PATCH 路由可以省略当前报文没有携带的字段。

ACK Pipeline 返回 messageId、开放 ackCode、结构化 data、可选设备 ACK 标识和可选发生时间。协议适配器为这次 ACK 结果分配平台 ACK 标识，再交给 DeviceAcknowledgementService 解释和保存。

Pipeline 阶段失败时抛出 PipelineExecutionException，`stageCode()` 等于实际失败阶段的 code。reported 定义调用 ACK 方法，或 ACK 定义调用 reported 方法时，抛出 IllegalStateException。

## 下行编码

`encode(message, clientId)` 按 DeviceDownlinkKind 和 target code 查找定义。desired 消息从 `message.data().get("value")` 取出单个属性值，并写入该路由的 DataKey。command 消息按每个 DataKey 的名称读取输入。MqttDownlinkFrame 同时包含标准 DeviceDownlink（`message()`）和已检查类型的 DataValues（`values()`）。

下行 Pipeline 返回厂商 payload 后，协议使用路由模板生成最终 Topic，并返回 EncodedMqttMessage。消息没有对应路由、数据缺少声明字段或字段 Java 类型错误时，编码失败。encoder 只生成 Topic 和 payload。业务 ACK 的处理、reported 与 desired 是否一致的判断，以及 RetryPolicy 由 IoT 消息服务负责。

## 物模型校验

`validate(thingModel)` 在协议绑定前检查：

- reported DataKey 名称对应 ThingProperty code。
- desired 路由对应一个 ThingProperty，并且只有一个 DataKey。
- command 路由对应 ThingCommand，DataKey 名称与全部 command input code 一致。
- 每个 DataKey 的 Class 与 ThingValueType 的标准 Java 类型一致。

标准类型映射为 Boolean、Long、BigDecimal、String、Instant、ThingObjectValue 和 ThingArrayValue。缺少 code、输入数量不一致或 Java 类型不同均抛出 IllegalArgumentException。

`ThingValueKind.javaType()` 是物模型、类型化 command 和 MQTT DataKey 校验共用的 Java 类型来源，不由各协议重复维护映射。`ThingValueType.pattern` 只适用于 string，并按 Java 正则表达式要求完整匹配。JSON 编解码保留这一约束。命令创建和协议输入使用同一校验结果。

## 示例

以下示例在上一节的协议基础上登记一个 command 下行。命令名为 `setPower`，输入为布尔值 `power`。

```java
DataKey<Boolean> power = DataKey.of("power", Boolean.class);

Pipeline<MqttDownlinkFrame, byte[]> powerCommand = Pipeline.of(PipelineStage.of(
        "encode-power-command",
        MqttDownlinkFrame.class,
        byte[].class,
        frame -> encodePowerFrame(frame.values().get(power))));

MqttDeviceProtocol protocol = MqttDeviceProtocol.builder("demo-json-v1")
        .reported(
                MqttUplinkRoute.topic("demo/+/reported"),
                "reported",
                ReportedMergeMode.SNAPSHOT,
                reported,
                power)
        .downlink(
                MqttDownlinkRoute.command("setPower"),
                powerCommand,
                power)
        .build();
```

`encodePowerFrame(...)` 是应用提供的编码方法，负责把布尔值转换为厂商 payload。`MqttDownlinkRoute.command("setPower")` 的输入 DataKey 必须与物模型中该 command 的输入 code 一致，否则 `validate(...)` 失败。

模块关系见 [EMQX 设备接入模块](/capabilities/iot/emqx)。Core 处理类型见 [Pipeline](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/pipeline/Pipeline.html)、[DataOps](/capabilities/data-configuration/data-operations) 和 [DataValues](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/data/DataValues.html)。
## 扩展方式

具体应用为每个设备协议创建一个 MqttDeviceProtocol，并注册为 Spring Bean。MqttProtocolCatalog 接收全部 MqttDeviceProtocol Bean，按 code 查找 Device 当前绑定的协议。
