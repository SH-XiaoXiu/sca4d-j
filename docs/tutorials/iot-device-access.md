# 接入第一台设备

接入设备先创建 Product、发布 ThingModel，再登记并启用 Device，最后按绑定协议连接和上报。开始前完成[第一次运行](/tutorials/local-start)与[登录](/tutorials/authentication-and-session)；下面先检查 EMQX、TimescaleDB，再创建一台本地温控器。

## 启动 IoT 依赖

确认 `.env/iot.env` 与 `.env/iot-database.env` 已从示例复制，再使用[第一次运行中的完整 Compose 命令](/tutorials/local-start#启动全部本地依赖)检查容器。该命令同时传入分片环境文件和两份 Compose 文件；已经启动完整环境时，不需要重复创建容器。

IoT Compose 会启动：

- EMQX，以及生成本地 Management API bootstrap key 的一次性容器；
- TimescaleDB，用于独立保存高频 IoT 历史数据。

默认本机入口为 MQTT `127.0.0.1:1883`、EMQX Dashboard `http://127.0.0.1:18083`、TimescaleDB `127.0.0.1:5433`。示例密钥和密码只适用于本机。

IoT Compose 直接把 EMQX HTTP Authenticator 指向宿主机 Gateway `8088`，不创建 Rule Engine、HTTP Connector 或 Action。Gateway 与 Server 必须按[第一次运行](/tutorials/local-start)使用相同的 `SCA4D_GATEWAY_SECRET`，Server 还必须使用与 Compose 相同的 `SCA4D_IOT_EMQX_WEBHOOK_SECRET`。只有 MQTT CONNECT 实际发生时，EMQX 才调用认证回调。

## 准备管理请求

```bash
BASE_URL=http://localhost:8088/api
ACCESS_TOKEN='<登录响应中的 data.session.accessToken>'
AUTH_HEADER="Authorization: Bearer $ACCESS_TOKEN"
```

当前账号必须拥有对应的 `iot:products:*` 和 `iot:devices:*` permission。各请求的字段和失败结果见[IoT Product、Device 与 EMQX](/reference/http/sca4d-server#iot-product、device-与-emqx)。

## 创建 Product

Sca4d Server 安装了 `standard-climate-json-v1` 和 `legacy-freezer-chaotic-v1` 两种示例协议。先创建默认停用的规范温控器 Product：

```bash
curl --fail-with-body --silent --show-error \
  -H "$AUTH_HEADER" \
  -H 'Content-Type: application/json' \
  -d '{
    "code":"standard-climate-controller",
    "i18n":{
      "zh-CN":{"displayName":"规范温控器"},
      "en-US":{"displayName":"Standard climate controller"}
    },
    "accessMethods":["ACCOUNT:AUTHENTICATION:SHARED_SECRET"],
    "protocolCodes":["standard-climate-json-v1"]
  }' \
  "$BASE_URL/iot/products"
```

保存响应中的 `data.id` 为 `PRODUCT_ID`。

## 发布物模型

发布请求同时提交完整技术模型和初始多语言字段，不是局部补丁。格式 2 的 `model` 不包含展示文本；`i18n` 使用稳定 JSON Pointer 路径标识模型和字段名称。下面的模型与 `StandardClimateDeviceProtocol.thingModel()` 对应：包含 `power`、`temperature`、`mode` 和 `set-mode`。协议会校验全部映射，只发布一个 `power` 属性无法使用这个协议。

```bash
PRODUCT_ID='<data.id>'

curl --fail-with-body --silent --show-error \
  -H "$AUTH_HEADER" \
  -H 'Content-Type: application/json' \
  --data-binary @- \
  "$BASE_URL/iot/products/$PRODUCT_ID/thing-models" <<'JSON'
{
  "model": {
    "formatVersion": 1,
    "productCode": "standard-climate-controller",
    "version": 1,
    "properties": [
      {
        "code": "power",
        "valueType": {
          "kind": "BOOLEAN"
        },
        "access": "READ_WRITE",
        "telemetry": false,
        "acknowledgementPolicy": "IOT/DOWNLINK/ACK/REPORTED/EQUALS/DESIRED",
        "retryPolicy": "IOT/DOWNLINK/RETRY/STATE/SYNC/DEFAULT"
      },
      {
        "code": "temperature",
        "valueType": {
          "kind": "DECIMAL",
          "minimum": "-40",
          "maximum": "100",
          "scale": 1,
          "unit": "celsius"
        },
        "access": "READ_ONLY",
        "telemetry": true
      },
      {
        "code": "mode",
        "valueType": {
          "kind": "ENUM",
          "enumValues": [
            {
              "code": "cool"
            },
            {
              "code": "fan"
            },
            {
              "code": "defrost"
            }
          ]
        },
        "access": "READ_ONLY",
        "telemetry": false
      }
    ],
    "events": [],
    "commands": [
      {
        "code": "set-mode",
        "inputs": [
          {
            "code": "mode",
            "valueType": {
              "kind": "ENUM",
              "enumValues": [
                {
                  "code": "cool"
                },
                {
                  "code": "fan"
                },
                {
                  "code": "defrost"
                }
              ]
            }
          }
        ],
        "acknowledgementPolicy": "IOT/DOWNLINK/ACK/DEVICE/RESULT",
        "retryPolicy": "IOT/DOWNLINK/RETRY/COMMAND/DEFAULT"
      }
    ]
  },
  "i18n": {
    "zh-CN": {
      "/displayName": "规范温控器",
      "/properties/power/displayName": "电源",
      "/properties/temperature/displayName": "温度",
      "/properties/mode/displayName": "模式",
      "/commands/set-mode/displayName": "设置模式",
      "/commands/set-mode/inputs/mode/displayName": "模式",
      "/properties/mode/valueType/enumValues/cool/displayName": "制冷",
      "/properties/mode/valueType/enumValues/fan/displayName": "送风",
      "/properties/mode/valueType/enumValues/defrost/displayName": "除霜",
      "/commands/set-mode/inputs/mode/valueType/enumValues/cool/displayName": "制冷",
      "/commands/set-mode/inputs/mode/valueType/enumValues/fan/displayName": "送风",
      "/commands/set-mode/inputs/mode/valueType/enumValues/defrost/displayName": "除霜"
    }
  }
}
JSON
```

Product code、协议支持、JSON 约束或默认 Locale 路径不匹配时，发布失败且不会保存部分模型。发布后可以通过 `PUT /iot/products/{productId}/thing-models/{version}/i18n` 完整替换翻译，不会生成新模型版本。

## 启用 Product 并创建设备

```bash
curl --fail-with-body --silent --show-error \
  -X PATCH \
  -H "$AUTH_HEADER" \
  -H 'Content-Type: application/json' \
  -d '{"status":"ENABLED"}' \
  "$BASE_URL/iot/products/$PRODUCT_ID/status"

curl --fail-with-body --silent --show-error \
  -H "$AUTH_HEADER" \
  -H 'Idempotency-Key: tutorial-climate-1' \
  -H 'Content-Type: application/json' \
  -d "{
    \"productId\":\"$PRODUCT_ID\",
    \"code\":\"climate-1\",
    \"serialNumber\":\"tutorial-0001\",
    \"displayName\":\"教程温控器\",
    \"remark\":\"本地示例\",
    \"thingModelVersion\":1,
    \"protocolCode\":\"standard-climate-json-v1\",
    \"connectionProtocol\":\"mqtt\",
    \"connectionIdentityType\":\"mqtt-client-id\",
    \"connectionIdentity\":\"climate-1\",
    \"accessMethods\":[\"ACCOUNT:AUTHENTICATION:SHARED_SECRET\"]
  }" \
  "$BASE_URL/iot/devices"
```

创建响应包含设备资料和一次性 Secret。立即把 Secret 交给本地模拟器或设备的安全配置，不要写入文档、日志或版本库；在创建幂等窗口结束后，丢失 Secret 只能通过重置接口签发新值。重复同一创建请求应复用相同 `Idempotency-Key`，不要为一次业务动作生成多个设备。

保存 `data.device.id` 为 `DEVICE_ID`，再启用设备：

```bash
DEVICE_ID='<data.device.id>'

curl --fail-with-body --silent --show-error \
  -X PATCH \
  -H "$AUTH_HEADER" \
  -H 'Content-Type: application/json' \
  -d '{"status":"ENABLED"}' \
  "$BASE_URL/iot/devices/$DEVICE_ID/status"
```

## 检查管理结果

```bash
curl --fail-with-body --silent --show-error \
  -H "$AUTH_HEADER" \
  "$BASE_URL/iot/products/$PRODUCT_ID"

curl --fail-with-body --silent --show-error \
  -H "$AUTH_HEADER" \
  "$BASE_URL/iot/devices/$DEVICE_ID"
```

查询结果应显示 Product 与 Device 均已启用，Device 绑定 `standard-climate-json-v1` 和物模型版本 1。到这里仅完成登记，还需要设备真实连接并上报。

## 连接温控器模拟器

使用仓库自带的 `apps/sca4d-iot-simulator`。在单独终端的进程环境中设置以下值；Secret 从创建响应的 `data.sharedSecret` 取得，通过本地秘密管理方式注入，不粘贴进命令历史或提交到 Git。

| 环境变量 | 本例使用值 |
|---|---|
| `SIMULATOR_DEVICE` | `standard`，只启动温控器 |
| `SIMULATOR_STANDARD_CLIENT_ID` | `climate-1`，必须等于 Device 的 `connectionIdentity` |
| `SIMULATOR_STANDARD_SECRET` | 创建响应中的 Shared Secret |
| `SIMULATOR_MQTT_HOST` | `localhost` |
| `SIMULATOR_MQTT_PORT` | `1883` |

在仓库根目录运行：

```bash
nice -n 10 ./gradlew --no-daemon --max-workers=1 \
  -Dorg.gradle.jvmargs="-Xmx1g -XX:MaxMetaspaceSize=384m" \
  :apps:sca4d-iot-simulator:run
```

模拟器使用用户名 `mqtt-shared-secret`，连接后订阅 `demo/standard/climate-1/downlink`，默认每 5 秒向 `demo/standard/climate-1/reported` 上报。日志应出现连接、下行订阅安装和状态上报结果。

然后读取 `GET /api/iot/devices/{deviceId}/runtime`，确认连接与 reported 中的 `power`、`temperature`、`mode` 已出现。只看到模拟器连接日志还不足以证明 Server 已接收状态。

连接被拒绝时，依次核对 Device/Product 启用状态、Client ID、Shared Secret 和 EMQX 回调凭据。连接成功却没有 reported 时，核对 Server MQTT runtime、协议 Topic 和上报解析日志。确认上报后继续[修改状态与发送命令](/tutorials/iot-state-and-commands)。

## MqttDeviceProtocol

源码：`modules/iot/sca4dj-iot-emqx/src/main/java/com/sca4d/iot/emqx/protocol/MqttDeviceProtocol.java`

### 处理对象

MqttDeviceProtocol 是 DeviceUplinkProtocol 的 MQTT 标准实现，使用一个稳定 code 保存 MQTT 上行路由、下行路由、Core Pipeline 和物模型 DataKey 映射。具体应用创建并登记协议。MqttProtocolCatalog 汇总 MQTT Topic 和 ACL；真正解码上行时，DeviceUplinkDispatcher 按 Device 当前的 `protocolCode` 从通用 DeviceProtocolCatalog 选择协议。

协议包含三类定义：

| 定义 | Pipeline 输入 | Pipeline 输出 | 业务结果 |
|---|---|---|---|
| reported 上行 | `byte[]` MQTT payload | DataValues | ReportedStateUpdate |
| ACK 上行 | `byte[]` MQTT payload | DecodedMqttAcknowledgement | DeviceAcknowledgement |
| desired 或 command 下行 | MqttDownlinkFrame | `byte[]` 厂商 payload | EncodedMqttMessage |

Topic、字段名、厂商 tag、字节序、校验范围和固件兼容规则写在 Pipeline 阶段中。MqttDeviceProtocol 负责选择 Pipeline、准备 DataValues、限制 reported 字段范围和验证物模型映射。

### 创建协议

`builder(code)` 创建协议构建器。`reported(...)` 登记 Topic Filter、字段组 code、ReportedMergeMode、`Pipeline<byte[], DataValues>` 和允许映射的 DataKey。`acknowledgement(...)` 登记 Topic Filter 与 `Pipeline<byte[], DecodedMqttAcknowledgement>`。`downlink(...)` 登记 desired 属性或 command 路由、`Pipeline<MqttDownlinkFrame, byte[]>` 和下行输入 DataKey。

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

Builder 拒绝重复上行 Topic Filter、重复的下行 kind 与 target code、重复 DataKey 和没有任何路由的协议。每条 reported 和下行定义至少声明一个 DataKey。

### Topic 路由

`MqttUplinkRoute.topic(filter)` 把 Topic Filter 中唯一的 `+` 层解释为 Topic 设备身份。Filter 含多个 `+` 时，调用 `topic(filter, identityLevel)` 明确对应层。默认情况下 Topic 身份等于 MQTT Client ID；`topicIdentity(...)` 可以安装 MqttTopicIdentityMapper，例如把 Client ID `gateway-device-001` 与 Topic 身份 `device-001` 相互转换。协议 Filter 从真实业务 Topic 开始；EmqxMqttRuntime 在订阅时添加 `$share/<group>/`。

MqttMessageRouter 为每条标准上行定义创建路由。路由从 Topic 提取身份，经 MqttTopicIdentityMapper 还原 MQTT Client ID，再使用 DeviceConnectionResolver 定位 Device。随后它只提交 `deviceId`、Topic Filter、payload 和观察时间；StandardDeviceUplinkDispatcher 再按 Device 台账选择实际协议。Topic 没有唯一匹配路由或 Client ID 未登记时，当前消息失败。

`MqttDownlinkRoute.desired(...)` 和 `command(...)` 按 kind 与 target code 选择 encoder。Topic 模板必须包含 `{identity}`，生成精确 Topic 时先把 Device MQTT Client ID 转换为 Topic 身份，再替换该占位符。

并非所有经 MQTT 到达的报文都必须使用 MqttDeviceProtocol。具体应用可以登记 MqttMessageRoute，解析网关中继帧并定位实际 Device，再把 DeviceProtocolMessage 交给同一个 DeviceUplinkDispatcher。这样的设备使用自己的 DeviceUplinkProtocol；MQTT 只负责把报文带入站。

### reported 与 ACK

MqttUplinkDefinition 在执行 Pipeline 前复制 payload。reported Pipeline 返回 DataValues 后，定义只读取登记的 DataKey：

- `coveredProperties` 包含该路由登记的全部属性 code；
- `values` 只包含本次 DataValues 实际提供的键；
- SNAPSHOT 路由通常要求协议阶段产出完整字段；
- PATCH 路由可以省略当前报文没有携带的字段。

ACK Pipeline 返回 messageId、开放 ackCode、结构化 data、可选设备 ACK 标识和可选发生时间。MqttDeviceProtocol 把结果转换为 DeviceUplink；DeviceUplinkDispatcher 再交给 DeviceUplinkReceiver 解释和保存。

Pipeline 阶段失败时抛出 PipelineExecutionException，`stageCode()` 等于实际失败阶段的 code。reported 定义调用 ACK 方法或 ACK 定义调用 reported 方法时抛出 IllegalStateException。

### 下行编码

`encode(message, clientId)` 按 DeviceDownlinkKind 和 target code 查找定义。desired 消息从 `message.data().get("value")` 取出单个属性值并写入该路由的 DataKey；command 消息按每个 DataKey 的名称读取输入。MqttDownlinkFrame 同时包含标准 DeviceDownlink 和已检查类型的 DataValues。

下行 Pipeline 返回厂商 payload 后，协议使用路由模板生成最终 Topic，并返回 EncodedMqttMessage。消息没有对应路由、数据缺少声明字段或字段 Java 类型错误时编码失败。encoder 只生成 Topic 和 payload；业务 ACK、reported 收敛和 RetryPolicy 由 IoT 消息服务处理。

### 物模型校验

`validate(thingModel)` 在协议绑定前检查：

- reported DataKey 名称对应 ThingProperty code；
- desired 路由对应一个 ThingProperty，并且只有一个 DataKey；
- command 路由对应 ThingCommand，DataKey 名称与全部 command input code 一致；
- 每个 DataKey 的 Class 与 ThingValueType 的标准 Java 类型一致。

标准类型映射为 Boolean、Long、BigDecimal、String、Instant、ThingObjectValue 和 ThingArrayValue。缺少 code、输入数量不一致或 Java 类型不同均抛出 IllegalArgumentException。

`ThingValueKind.javaType()` 是物模型、类型化 command 和 MQTT DataKey 校验共同使用的 Java 类型来源，各协议不重复维护映射。`ThingValueType.pattern` 只适用于 string，必须同时声明 `maximumLength`，并按 Java 正则表达式要求完整匹配。模型构建和 JSON 解析时完成表达式编译；运行校验先拒绝超过最大长度的输入，再使用已编译表达式匹配。JSON 编解码和示例管理端都保留并显示该约束。

模块关系见 [EMQX 设备接入模块](/tutorials/iot-device-access)。Core 处理类型见 [Pipeline](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/pipeline/Pipeline.html)、[DataOps](/tutorials/backend-feature) 和 [DataValues](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/data/DataValues.html)。

## HmacCanonicalRequest

源码：`apps/sca4d-server/src/main/java/com/sca4d/app/server/iot/HmacCanonicalRequest.java`

HmacCanonicalRequest 定义 Sca4d Server 的 Device HTTP HMAC 签名输入。它属于 App，因为 HTTP 路由、请求头和 query 规范化规则不是通用 Auth 或 IoT 模块的职责。

调用方必须传入正数 deviceId、大写前可规范化的 HTTP method、以 `/` 开始且不含换行的 path、已经按名称和值排序并使用 RFC 3986 百分号编码的 query、完整 body、秒级时间和 16 到 128 个可打印 ASCII 字符的 nonce。

`canonicalBytes()` 返回以下 UTF-8 内容，其中 body 使用 SHA-256 小写十六进制摘要：

```text
IOT-HMAC-SHA256
{deviceId}
{METHOD}
{path}
{canonicalQuery}
{bodySha256}
{epochSecond}
{nonce}
```

HmacAuthenticationMethod 只验证这些字节和签名，不知道 HTTP 字段如何产生。
