# 接入第一台设备

场景：一台温控器（Thermostat）通过 MQTT 上报电源、温度和模式，Server 保存最新的 reported 状态。

接入分为六步：启动依赖、定义设备协议、创建 Product、发布物模型、创建并启用 Device、让设备连接并上报。

前置条件：已完成[第一次运行](/tutorials/local-start)和[登录](/tutorials/authentication-and-session)。当前账号拥有 `iot:products:*` 与 `iot:devices:*` 对应的权限。

HTTP 路由由具体应用提供。字段和失败结果见 [IoT Product 与 Device](/reference/http/sca4d-server#iot-product-与-device)。

## 实现步骤

### 1. 启动 IoT 依赖

条件：`.env/iot.env` 与 `.env/iot-database.env` 已从示例复制。

使用[第一次运行中的完整 Compose 命令](/tutorials/local-start#启动全部本地依赖)启动容器。该命令同时传入分片环境文件和两份 Compose 文件。完整环境已经启动时，不需要重复执行。

IoT Compose 启动以下服务：

- EMQX，以及生成本地 Management API bootstrap key 的一次性容器；
- TimescaleDB，独立保存高频 IoT 历史数据。

默认本机入口如下：

| 服务 | 地址 | 配置键 |
|---|---|---|
| MQTT | `127.0.0.1:1883` | `SCA4DJ_IOT_EMQX_DEVICE_MQTT_BIND` |
| EMQX Dashboard | `http://127.0.0.1:18083` | `SCA4DJ_IOT_EMQX_DASHBOARD_BIND` |
| TimescaleDB | `127.0.0.1:55434` | `.env/iot-database.env` 中的 `*_TIMESCALE_BIND` |

EMQX 的 HTTP Authenticator 指向宿主机 Gateway 的 `/callbacks/iot.emqx.authenticate`，默认端口 `8088`。Compose 不创建 Rule Engine、HTTP Connector 或 Action。

Gateway 与 Server 必须使用相同的 `SCA4DJ_GATEWAY_SECRET`。Server 与 Compose 必须使用相同的 `SCA4DJ_IOT_EMQX_WEBHOOK_SECRET`。示例密钥和密码只适用于本机。

### 2. 定义设备协议

协议决定 Topic、报文格式和物模型字段的对应关系。下面的类由具体应用定义，放在应用的配置包中。
协议只描述报文与字段的对应，传输只负责带入报文，物模型描述业务能力，三者分别声明。

```java
import java.math.BigDecimal;

import com.sca4d.core.api.data.DataKey;
import com.sca4d.core.api.data.DataOps;
import com.sca4d.core.api.data.DataValues;
import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.pipeline.PipelineStage;
import com.sca4d.iot.api.state.ReportedMergeMode;
import com.sca4d.iot.emqx.protocol.MqttDeviceProtocol;
import com.sca4d.iot.emqx.protocol.MqttUplinkRoute;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 由具体应用定义：温控器的 MQTT 协议。 */
@Configuration
public class ThermostatProtocolConfiguration {

    static final DataKey<Boolean> POWER = DataKey.of("power", Boolean.class);
    static final DataKey<BigDecimal> TEMPERATURE = DataKey.of("temperature", BigDecimal.class);
    static final DataKey<String> MODE = DataKey.of("mode", String.class);

    @Bean
    MqttDeviceProtocol thermostatProtocol() {
        Pipeline<byte[], DataValues> reported = Pipeline
                .of(PipelineStage.of(
                        "parse-reported-json",
                        byte[].class,
                        DataOps.JsonPipe.class,
                        payload -> DataOps.json(payload)))
                .then(PipelineStage.of(
                        "map-reported-fields",
                        DataOps.JsonPipe.class,
                        DataValues.class,
                        json -> DataValues.builder()
                                .put(POWER, json.requiredBoolean("power"))
                                .put(TEMPERATURE, json.requiredDecimal("temperature"))
                                .put(MODE, json.requiredText("mode"))
                                .build()));

        return MqttDeviceProtocol.builder("thermostat-json-v1")
                .reported(
                        MqttUplinkRoute.topic("thermostat/+/reported"),
                        "reported",
                        ReportedMergeMode.SNAPSHOT,
                        reported,
                        POWER, TEMPERATURE, MODE)
                .build();
    }
}
```

对应的上行报文如下，Topic 为 `thermostat/climate-1/reported`：

```json
{"power": true, "temperature": 23.5, "mode": "cool"}
```

要点：

- `MqttUplinkRoute.topic(filter)` 把 Filter 中唯一的 `+` 层解释为设备身份。默认情况下，身份等于 MQTT Client ID。
- `ReportedMergeMode.SNAPSHOT` 表示本次缺少的属性从 latest 状态清除。`PATCH` 表示保留原值。
- `MqttDeviceProtocol` 类型的 Bean 由自动配置收集。协议 code 重复时，启动失败。

### 3. 创建 Product

先准备请求变量：

```bash
BASE_URL=http://localhost:8088/api
ACCESS_TOKEN='<登录响应中的 data.session.accessToken>'
AUTH_HEADER="Authorization: Bearer $ACCESS_TOKEN"
```

创建默认停用的 Product：

```bash
curl --fail-with-body --silent --show-error \
  -H "$AUTH_HEADER" \
  -H 'Content-Type: application/json' \
  -d '{
    "code":"thermostat",
    "i18n":{
      "zh-CN":{"displayName":"温控器"},
      "en-US":{"displayName":"Thermostat"}
    },
    "accessMethods":["ACCOUNT:AUTHENTICATION:SHARED_SECRET"],
    "protocolCodes":["thermostat-json-v1"]
  }' \
  "$BASE_URL/iot/products"
```

保存响应中的 `data.id` 为 `PRODUCT_ID`。

### 4. 发布物模型

发布请求同时提交完整技术模型和初始多语言字段，不是局部补丁。`model` 不包含展示文本。`i18n` 使用 JSON Pointer 路径标识模型和字段名称。

模型必须包含协议引用的全部属性。协议在发布时校验 `power`、`temperature` 和 `mode` 的 code 与值类型。模型同时声明 `set-mode` 命令，供[下一篇](/tutorials/iot-state-and-commands)使用。

```bash
curl --fail-with-body --silent --show-error \
  -H "$AUTH_HEADER" \
  -H 'Content-Type: application/json' \
  --data-binary @- \
  "$BASE_URL/iot/products/$PRODUCT_ID/thing-models" <<'JSON'
{
  "model": {
    "formatVersion": 1,
    "productCode": "thermostat",
    "version": 1,
    "properties": [
      {
        "code": "power",
        "valueType": {"kind": "BOOLEAN"},
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
          "kind": "NUM",
          "enumValues": [{"code": "cool"}, {"code": "fan"}, {"code": "defrost"}]
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
              "kind": "NUM",
              "enumValues": [{"code": "cool"}, {"code": "fan"}, {"code": "defrost"}]
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
      "/displayName": "温控器",
      "/properties/power/displayName": "电源",
      "/properties/temperature/displayName": "温度",
      "/properties/mode/displayName": "模式",
      "/properties/mode/valueType/enumValues/cool/displayName": "制冷",
      "/properties/mode/valueType/enumValues/fan/displayName": "送风",
      "/properties/mode/valueType/enumValues/defrost/displayName": "除霜",
      "/commands/set-mode/displayName": "设置模式",
      "/commands/set-mode/inputs/mode/displayName": "模式",
      "/commands/set-mode/inputs/mode/valueType/enumValues/cool/displayName": "制冷",
      "/commands/set-mode/inputs/mode/valueType/enumValues/fan/displayName": "送风",
      "/commands/set-mode/inputs/mode/valueType/enumValues/defrost/displayName": "除霜"
    }
  }
}
JSON
```

要点：

- 枚举值类型的 `kind` 是 `NUM`，候选值写在 `enumValues` 中。
- `telemetry` 为 `true` 的属性，在 reported 提交后同时发布为历史样本。
- `access` 为 `READ_WRITE` 的属性才可以设置 desired，并且需要 `acknowledgementPolicy` 与 `retryPolicy`。
- 发布后，可以用 `PUT /iot/products/{productId}/thing-models/{version}/i18n` 完整替换翻译。这一操作不生成新的模型版本。

### 5. 启用 Product 并创建 Device

```bash
curl --fail-with-body --silent --show-error \
  -X PATCH \
  -H "$AUTH_HEADER" \
  -H 'Content-Type: application/json' \
  -d '{"status":"ENABLED"}' \
  "$BASE_URL/iot/products/$PRODUCT_ID/status"

curl --fail-with-body --silent --show-error \
  -H "$AUTH_HEADER" \
  -H 'Idempotency-Key: tutorial-thermostat-1' \
  -H 'Content-Type: application/json' \
  -d "{
    \"productId\":\"$PRODUCT_ID\",
    \"code\":\"climate-1\",
    \"serialNumber\":\"tutorial-0001\",
    \"displayName\":\"教程温控器\",
    \"thingModelVersion\":1,
    \"protocolCode\":\"thermostat-json-v1\",
    \"connectionProtocol\":\"mqtt\",
    \"connectionIdentityType\":\"mqtt-client-id\",
    \"connectionIdentity\":\"climate-1\",
    \"accessMethods\":[\"ACCOUNT:AUTHENTICATION:SHARED_SECRET\"]
  }" \
  "$BASE_URL/iot/devices"
```

创建响应包含设备资料和一次性 Secret。立即把 `data.sharedSecret` 交给设备的安全配置，不写入文档、日志或版本库。

保存 `data.device.id` 为 `DEVICE_ID`，再启用 Device：

```bash
DEVICE_ID='<data.device.id>'

curl --fail-with-body --silent --show-error \
  -X PATCH \
  -H "$AUTH_HEADER" \
  -H 'Content-Type: application/json' \
  -d '{"status":"ENABLED"}' \
  "$BASE_URL/iot/devices/$DEVICE_ID/status"
```

### 6. 连接设备并上报

使用任意 MQTT 客户端，按下表连接：

| 项 | 值 |
|---|---|
| Host 与端口 | `localhost`、`1883` |
| Client ID | `climate-1`，必须等于 Device 的 `connectionIdentity` |
| 用户名与密码 | 由具体应用的 `EmqxDeviceConnectionRequestFactory` 解释；使用 Shared Secret 时，密码为创建响应中的 `data.sharedSecret` |
| 发布 Topic | `thermostat/climate-1/reported`，QoS 1 |
| 报文 | `{"power": true, "temperature": 23.5, "mode": "cool"}` |

EMQX 的 ACL 由协议生成：设备只能向自己的上行 Topic 发布，只能订阅自己的精确下行 Topic，其余 Topic 一律拒绝。

## 验证

查询 Product 与 Device：

```bash
curl --fail-with-body --silent --show-error \
  -H "$AUTH_HEADER" \
  "$BASE_URL/iot/products/$PRODUCT_ID"

curl --fail-with-body --silent --show-error \
  -H "$AUTH_HEADER" \
  "$BASE_URL/iot/devices/$DEVICE_ID"
```

结果应显示 Product 与 Device 均为启用状态，Device 绑定 `thermostat-json-v1` 和物模型版本 1。

设备发布报文后，读取运行态：

```bash
curl --fail-with-body --silent --show-error \
  -H "$AUTH_HEADER" \
  "$BASE_URL/iot/devices/$DEVICE_ID/runtime"
```

结果应包含当前连接信息，以及 reported 中的 `power`、`temperature` 和 `mode`。只看到客户端连接成功，不能证明 Server 已保存状态。

启用 TimescaleDB 后，`temperature` 样本可以通过 `GET /iot/devices/{deviceId}/telemetry` 读取。

接下来阅读[修改状态与发送命令](/tutorials/iot-state-and-commands)。

## 失败结果与限制

| 现象 | 原因与处理 |
|---|---|
| 发布物模型返回 `2910` | 物模型 JSON 语法错误。核对括号和引号。 |
| 发布物模型返回 `2911` | `formatVersion` 不受支持。当前格式版本为 `1`。 |
| 发布物模型返回 `2912` | 字段错误，消息参数携带 RFC 6901 路径。按路径修正，例如把 `ENUM` 改为 `NUM`。 |
| 发布物模型失败，且没有保存部分模型 | Product code、协议支持、JSON 约束或默认 Locale 路径不匹配。 |
| 启用 Product 返回冲突 | 协议或已发布模型为空，或已登记协议的 `validate(thingModel)` 未通过。Product 保持停用。 |
| MQTT 连接被拒绝 | 依次核对 Device 与 Product 是否启用、Client ID、Shared Secret，以及 EMQX 到 Gateway 回调的 `SCA4DJ_IOT_EMQX_WEBHOOK_SECRET`。认证失败统一返回 `deny`。 |
| 连接成功，但 `runtime` 没有 reported | 核对 Topic 是否匹配协议的上行 Filter，以及报文字段类型。 |
| Server 日志出现消息被拒绝 | Topic 结构错误、Client ID 未登记，或报文字段不符合 Pipeline。三种原因分别对应 `TOPIC_INVALID`、`IDENTITY_INVALID` 和 `MESSAGE_INVALID`。消息被丢弃，不会因再次处理而恢复。 |
| 创建 Device 重复提交 | 同一次创建动作复用同一个 `Idempotency-Key`。幂等窗口结束后，丢失的 Secret 只能通过重置接口签发新值。 |

协议校验在绑定物模型前执行。`MqttDeviceProtocol.validate` 检查以下条件，不满足时抛出 `IllegalArgumentException`：

- reported 的 `DataKey` 名称对应物模型属性 code；
- 每个 `DataKey` 的 Java 类型与 `ThingValueKind.javaType()` 一致，对应关系为 `BOOLEAN` 到 `Boolean`、`INTEGER` 到 `Long`、`DECIMAL` 到 `BigDecimal`、`STRING` 与 `NUM` 到 `String`、`TIMESTAMP` 到 `Instant`。

`MqttDeviceProtocol.Builder` 拒绝重复的上行 Topic Filter、重复的下行 kind 与 target code、重复的 `DataKey`，以及没有任何路由的协议。

## 相关页面

- [修改状态与发送命令](/tutorials/iot-state-and-commands)
- [第一次运行](/tutorials/local-start)
- [IoT 设备生命周期](/home/iot)
- [EMQX 接入模块](/capabilities/iot/emqx)
- [MQTT 设备协议](/capabilities/iot/mqtt-device-protocol)
- [设备 HMAC 请求签名](/capabilities/iot/hmac-request-signing)
- [Sca4d Server 接口参考](/reference/http/sca4d-server)
