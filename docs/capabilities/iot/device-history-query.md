# DeviceHistoryQueryService

DeviceHistoryQueryService 读取指定 Device 的下行状态、ACK、事件和 Telemetry 历史。具体应用负责权限、HTTP 参数绑定和响应格式。DeviceHistoryQueryService 只返回模块公开类型。

## 数据来源

| 方法 | 数据与顺序 | 存储要求 |
|---|---|---|
| `downlinks` | 下行消息，按创建时间倒序分页 | 主 PostgreSQL |
| `acknowledgements` | 业务 ACK，按接收时间倒序分页 | 提供 DeviceHistoryStore 实现时读取完整 ACK 阶段；未提供时读取主 PostgreSQL 保存的每条下行最新 ACK |
| `events` | 离散 Device 事件，按发生时间倒序分页 | 具体应用提供的 DeviceHistoryStore |
| `telemetry` | 半开时间范围 `[from, to)` 内的属性样本，按观测时间升序 | 具体应用提供的 DeviceHistoryStore |

分页页码从 1 开始，页大小为 1 到 100。Telemetry 查询的 `limit` 不能超过历史存储配置上限。内置 TimescaleDB 实现使用 `sca4dj.iot.timescale.max-query-results`，默认 1000，可配置范围为 1 到 10000。`to` 必须晚于 `from`。按设备查询的方法先确认 Device 存在。未提供 DeviceHistoryStore 时，`events` 和 `telemetry` 抛出依赖不可用错误，不返回空集合，以免被误认为没有历史。

## 每个设备或消息的最近记录

`recentDownlinks`、`recentEvents` 按设备集合读取每台设备最近的下行和事件。`recentAcknowledgements` 按下行消息集合读取每条消息最近的回执。返回值按输入标识分组，无记录的标识对应空列表，映射与列表均不可修改。下行按创建时间倒序、事件按发生时间倒序、回执按接收时间倒序排列，与对应单对象查询一致。

输入标识必须为正数，重复标识合并。空集合返回空映射。每个对象的记录上限为 1 到 100。按设备集合的查询先批量校验设备存在性，任一设备不存在时抛出设备不存在错误。消息已经清理或没有回执时返回空列表。

批量查询在数据库中为每个对象截取最近记录，不统计完整历史数量。Repository 每批最多查询 500 个标识，并继续遵守公共 SQL 批次条目和绑定参数限制。具体应用需要组装多个设备的时间线时，使用上述批量方法，避免逐设备或逐消息发起查询。

回执按消息分别选择来源。历史存储中有该消息的记录时，返回历史回执。消息没有历史回执，或未提供历史存储时，返回主库中保存的该消息最新 ACK。历史查询实际失败时向调用方抛出异常，不把失败当作空历史。非空设备集合的事件查询未提供历史存储时，仍抛出依赖不可用错误。

主库与高频历史各自负责的内容见 [IoT 基础模块](/capabilities/iot/iot)。

## 示例

以下示例对应温控器（Thermostat）的运维页面。页面先按设备集合批量读取最近下行，再读取一台设备一小时内的温度样本。

```java
import com.sca4d.iot.api.query.DeviceHistoryQueryService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class ThermostatHistoryPage {

    private final DeviceHistoryQueryService history;

    public ThermostatHistoryPage(DeviceHistoryQueryService history) {
        this.history = history;
    }

    public void load(List<Long> thermostatIds, long thermostatId) {
        Map<Long, ?> recentDownlinks = history.recentDownlinks(thermostatIds, 5);

        Instant to = Instant.now();
        Instant from = to.minus(Duration.ofHours(1));
        List<?> samples = history.telemetry(thermostatId, "temperature", from, to, 500);

        // 应用把结果转换为页面所需的响应对象。
    }
}
```

`telemetry` 的 `limit` 必须不超过历史存储的查询上限。分页查询（如 `downlinks(deviceId, page)`）使用 `PageRequest`，页码从 1 开始。
## 扩展方式

具体应用通过 Spring Bean 提供 `DeviceHistoryStore` 实现。IoT 自动配置通过 `ObjectProvider<DeviceHistoryStore>` 获取实现，未提供时只能使用主库数据。`sca4dj-iot-timescale` 模块提供 `TimescaleDeviceHistoryStore` 实现，启用条件和表结构见 [IoT TimescaleDB 历史模块](/capabilities/iot/timescale)。
