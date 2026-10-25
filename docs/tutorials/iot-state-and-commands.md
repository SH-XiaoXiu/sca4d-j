# 修改状态与发送命令

场景：温控器（Thermostat）已经接入并上报。现在需要把电源持续设置为关闭，并发送一次把模式切换为 `fan` 的命令，再确认设备真正执行。

三类数据各有用途：

| 数据 | 含义 | 入口 |
|---|---|---|
| reported | 设备实际上报的值 | 设备上行，Server 只读取 |
| desired | 持续期望值 | `PUT /iot/devices/{deviceId}/desired` |
| command | 一次性动作 | `POST /iot/devices/{deviceId}/commands/{commandCode}` |

前置条件：已完成[接入第一台设备](/tutorials/iot-device-access)，Device 已启用并能上报。当前账号拥有 `iot:devices:operate` 和 `iot:devices:read` 权限。

消息发出和连接成功都不能证明设备已执行。每次提交都保存返回的下行 `messageId`，用它查询状态和业务 ACK。
每条 desired 或 command 先在业务事务中登记为下行消息，再由消息 Consumer 获取锁后发送。发布成功只代表消息到达 Broker，设备执行由 ACK 或 reported 值达到 desired 后确认。

## 实现步骤

### 1. 在协议中追加下行与 ACK 路由

物模型已经包含 `power` 属性和 `set-mode` 命令。协议需要补充三条路由：desired 编码、command 编码和 ACK 解码。

以下代码在[接入第一台设备](/tutorials/iot-device-access)的 `thermostatProtocol()` 中，接在 `.reported(...)` 之后。类型由具体应用定义。

需要追加导入 `java.util.Map`，以及 `com.sca4d.iot.emqx.protocol` 包中的 `DecodedMqttAcknowledgement`、`MqttDownlinkFrame` 和 `MqttDownlinkRoute`。

```java
Pipeline<MqttDownlinkFrame, byte[]> encodePower = Pipeline.of(PipelineStage.of(
        "encode-power",
        MqttDownlinkFrame.class,
        byte[].class,
        frame -> DataOps.obj()
                .put("messageId", String.valueOf(frame.message().messageId()))
                .put("power", frame.values().get(POWER))
                .bytes()));

Pipeline<MqttDownlinkFrame, byte[]> encodeMode = Pipeline.of(PipelineStage.of(
        "encode-set-mode",
        MqttDownlinkFrame.class,
        byte[].class,
        frame -> DataOps.obj()
                .put("messageId", String.valueOf(frame.message().messageId()))
                .put("mode", frame.values().get(MODE))
                .bytes()));

Pipeline<byte[], DecodedMqttAcknowledgement> decodeAck = Pipeline
        .of(PipelineStage.of(
                "parse-ack-json",
                byte[].class,
                DataOps.JsonPipe.class,
                payload -> DataOps.json(payload)))
        .then(PipelineStage.of(
                "map-ack",
                DataOps.JsonPipe.class,
                DecodedMqttAcknowledgement.class,
                json -> new DecodedMqttAcknowledgement(
                        Long.parseLong(json.requiredText("messageId")),
                        json.requiredText("result"),
                        Map.of(),
                        null,
                        null)));

return MqttDeviceProtocol.builder("thermostat-json-v1")
        .reported(/* 与上一篇相同 */)
        .downlink(
                MqttDownlinkRoute.desired("power", "thermostat/{identity}/downlink/power"),
                encodePower,
                POWER)
        .downlink(
                MqttDownlinkRoute.command("set-mode", "thermostat/{identity}/downlink/set-mode"),
                encodeMode,
                MODE)
        .acknowledgement(MqttUplinkRoute.topic("thermostat/+/ack"), decodeAck)
        .build();
```

要点：

- Topic 模板必须包含 `{identity}`，不能包含通配符。生成精确 Topic 时，Client ID 先转换为 Topic 身份，再替换占位符。
- desired 路由只有一个 `DataKey`，其名称对应属性 code。command 路由的 `DataKey` 名称必须与全部输入 code 一致，`set-mode` 的输入 code 是 `mode`。
- 不指定模板时，默认 Topic 为 `sca4dj/devices/{identity}/desired/<code>` 和 `sca4dj/devices/{identity}/commands/<code>`。
- 协议只生成 Topic 和 payload。ACK 判定、reported 与 desired 的一致性判断和重试由 IoT 模块处理。
- 设备 ACK 的 `ackCode` 由物模型中的策略解释。`IOT/DOWNLINK/ACK/DEVICE/RESULT` 把 `success` 视为成功，把 `failed` 视为失败，其他值继续等待。

### 2. 设置 desired

通过 Gateway 发送请求，把电源持续设置为关闭：

```http
PUT /api/iot/devices/{deviceId}/desired
Authorization: Bearer <accessToken>
Content-Type: application/json

{"set":{"power":false},"clear":[]}
```

响应返回最新的 `desiredSequence` 和本次实际生成的下行消息。保存消息的 `id`。

`set` 与 `clear` 只修改列出的属性。两者合计不能超过 `sca4dj.iot.max-desired-properties`，默认 100。

### 3. 提交 command

```http
POST /api/iot/devices/{deviceId}/commands/set-mode
Authorization: Bearer <accessToken>
Content-Type: application/json

{"mode":"fan"}
```

正文是命令输入对象，最多 64 项。`commandCode` 与输入必须符合 Device 当前绑定的物模型。

具体应用的 Service 直接调用时，使用 `DeviceCommandService`：

```java
// 由具体应用定义：在同一事务中修改业务数据并提交命令。
DeviceDownlink message = deviceCommandService.submit(
        deviceId,
        "set-mode",
        Map.of("mode", "fan"),
        null,                      // 首次发布的最晚时间，即时命令为空
        Duration.ofSeconds(30));   // 发送成功后等待设备 ACK 的时间
long messageId = message.messageId();
```

### 4. 让设备执行并回执

设备订阅自己的精确下行 Topic，执行后上报。

| 步骤 | Topic | 报文 |
|---|---|---|
| 收到 desired | `thermostat/climate-1/downlink/power` | `{"messageId":"<id>","power":false}` |
| 上报新状态 | `thermostat/climate-1/reported` | `{"power":false,"temperature":23.5,"mode":"cool"}` |
| 收到 command | `thermostat/climate-1/downlink/set-mode` | `{"messageId":"<id>","mode":"fan"}` |
| 回执 command | `thermostat/climate-1/ack` | `{"messageId":"<id>","result":"success"}` |
| 上报新模式 | `thermostat/climate-1/reported` | `{"power":false,"temperature":23.5,"mode":"fan"}` |

desired 使用 `IOT/DOWNLINK/ACK/REPORTED/EQUALS/DESIRED`，不需要单独回执。reported 中的 `power` 等于 desired 时，消息成功。

command 使用 `IOT/DOWNLINK/ACK/DEVICE/RESULT`，设备必须发送 `success` 或 `failed`。

### 5. 查询结果

使用具有 `iot:devices:read` 权限的 Token 查询：

```bash
curl --fail-with-body --silent --show-error \
  -H "$AUTH_HEADER" \
  "$BASE_URL/iot/devices/$DEVICE_ID/downlinks"

curl --fail-with-body --silent --show-error \
  -H "$AUTH_HEADER" \
  "$BASE_URL/iot/devices/$DEVICE_ID/acknowledgements"

curl --fail-with-body --silent --show-error \
  -H "$AUTH_HEADER" \
  "$BASE_URL/iot/devices/$DEVICE_ID/runtime"
```

具体应用的 Service 使用以下接口：

| 需要 | 调用 |
|---|---|
| 当前连接、reported、desired | `DeviceRuntimeQueryService.runtime(deviceId)` |
| 单条下行 | `DeviceHistoryQueryService.downlink(messageId)` |
| 一批下行 | `DeviceHistoryQueryService.downlinks(Collection<Long>)` |
| 某条下行的 ACK | `DeviceHistoryQueryService.acknowledgementsByMessageId(messageId, page)` |
| 多个 Device 的连接 | `DeviceConnectionQueryService.connections(Collection<Long>)` |

列表页展示下行状态时，使用批量的 `downlinks(Collection<Long>)`，不逐条查询。空集合不访问数据库。不存在或已按保留期删除的标识，不出现在结果中。

在线状态不由 `DeviceConnectionQueryService` 判断，而由具体应用选择的 `DeviceOnlinePolicy.isOnline(connections)` 计算。

## 验证

`downlinks` 中两条消息的状态依次变化如下：

```text
PENDING -> SENDING -> WAITING_ACK -> SUCCEEDED
```

`runtime` 的 reported 中，`power` 为 `false`，`mode` 为 `fan`。

`acknowledgements` 中出现 command 消息的 `success` 回执。

两条 command 是两个独立动作。需要再次切换模式时，提交新的 command，不要重复使用旧消息。

重复设置与当前相同的 desired 时，可能不生成新消息，响应中的消息数组为空。

## 失败结果与限制

| 现象 | 原因与处理 |
|---|---|
| desired 或 command 返回状态冲突 | Device 已停用。启用后再提交。 |
| 提交返回 `2908` | 输入键不完整，或值不符合当前物模型的范围、`pattern` 与枚举。按物模型修正输入。 |
| 提交返回 `2901` | Device 不存在。 |
| 消息状态为 `FAILED`，失败 code 为 `ACK_TIMEOUT` | 超过 ACK 等待时间。默认值由 `SCA4DJ_IOT_ACKNOWLEDGEMENT_TIMEOUT` 配置，示例为 `PT30S`。先查看设备日志，不要重复创建 command。 |
| 失败 code 为 `DEVICE_REPORTED_FAILURE` | 设备回执了 `failed`。 |
| 失败 code 为 `TRANSPORT_FAILED` | 协议发布失败。核对 EMQX 连接和 Server 的 MQTT runtime。 |
| 失败 code 为 `COMMAND_RETRY_EXHAUSTED` | command 按重试策略重试后仍未成功。 |
| 状态为 `SUPERSEDED` | 同一 desired 属性产生了更新的 `desiredSequence`，旧消息不再发送。 |
| 状态为 `EXPIRED` | 消息在首次成功发布前超过最晚发送时间。 |
| 编码失败，提示没有 MQTT Pipeline 或缺少声明字段 | 协议没有登记该 kind 与 target code 的下行路由，或数据缺少 `DataKey` 声明的字段。 |
| `events` 或 `telemetry` 抛出依赖不可用错误 | 没有登记 `DeviceHistoryStore`。历史查询不会以空集合表示没有历史。 |
| `telemetry` 的 `limit` 被拒绝 | 超过 `sca4dj.iot.timescale.max-query-results`，默认 1000，范围 1 到 10000。`to` 必须晚于 `from`。 |

取消未完成的 command：

```java
// 与自身的业务状态修改放在同一事务中。
boolean cancelled = deviceCommandService.cancel(messageId);
```

- 首次取消返回 `true`。消息已经进入终态时返回 `false`。
- 消息不存在或不是 command 时，抛出 `2908`。
- 取消后，发送、发布结果写回、ACK 超时和重试都不能恢复这条消息。
- 已经进入协议发布的命令仍可能送达设备，补偿命令由具体应用决定。
- 取消设备定时规则不会取消已经生成的 command。

状态的范围：

- `runtime(deviceId)` 保证三类数据属于同一个 Device 和同一个 `bindingGeneration`，不保证 Redis 连接信息与 PostgreSQL 状态属于同一时刻。
- 发送、ACK 和超时使用同一个 `iot:downlink:<messageId>` Redis 分布式锁。进程在协议发布后、结果事务前退出时，重复派发从 `SENDING` 继续。
- Telemetry 在提交后发布，失败不会回滚 latest reported。下行发送、ACK 超时、断连和设备定时结果使用 `messaging_outbox` 重试。
- 下行表的终态记录按保留期分批删除。`SUCCEEDED`、`FAILED`、`EXPIRED` 与 `SUPERSEDED` 都在清理范围内。
- `DeviceService`、`DesiredStateService` 和 `DeviceCommandService` 不判断谁能修改。权限和 Device 状态检查由具体应用在公开入口外层完成。

需要在固定时间或按 Cron 发送命令时，使用设备定时规则，不在业务代码中自行轮询。规则的创建、版本冲突和到期执行见[设备定时](/capabilities/iot/device-scheduling)。

## 相关页面

- [接入第一台设备](/tutorials/iot-device-access)
- [IoT 设备生命周期](/home/iot)
- [设备生命周期](/capabilities/iot/device-lifecycle)
- [设备运行态查询](/capabilities/iot/device-runtime-query)
- [设备历史查询](/capabilities/iot/device-history-query)
- [设备定时](/capabilities/iot/device-scheduling)
- [TimescaleDB 配置](/capabilities/iot/timescale-configuration)
- [Sca4d Server 接口参考](/reference/http/sca4d-server)
