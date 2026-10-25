# IoT 基础模块

`modules/iot/sca4dj-iot` 保存设备台账、当前状态、可靠下行和设备定时规则。它不拥有 Device 的 Account 或 Credential，不解析 MQTT Topic，不调用 EMQX Management API，也不保存遥测与事件历史。Product、Device、reported、desired、command、ACK 和定时的完整顺序见 [IoT 设备生命周期](/capabilities/iot/device-lifecycle)。

## 公开入口

模块通过 `sca4dj-iot-api` 中定义的接口与具体应用和协议适配器协作：

- Product、Device、ThingModel 与 DeviceProtocol 描述设备定义、实例和协议绑定。
- [DeviceConnectionAdmission](/javadoc/modules/iot/sca4dj-iot-api/com/sca4d/iot/api/connection/DeviceConnectionAdmission.html)、DeviceConnectionReceiver 与 DeviceConnectionTerminator 分别处理连接准入、当前连接事实和协议断连。
- DeviceUplinkReceiver 接收协议适配器转换后的 reported、事件与业务 ACK。
- DeviceTransport 由协议适配器实现，通用消息 Consumer 通过它发送标准下行。
- [ReportedStateStore](/javadoc/modules/iot/sca4dj-iot-api/com/sca4d/iot/api/state/ReportedStateStore.html) 保存 latest reported，可以由 PostgreSQL、Redis 或具体应用的其他实现提供。
- [DeviceHistoryStore](/javadoc/modules/iot/sca4dj-iot-api/com/sca4d/iot/api/history/DeviceHistoryStore.html) 读取遥测、事件、ACK 和设备定时执行历史。IoT 基础模块只依赖接口定义，不拥有历史数据库。
- [DeviceRuntimeQueryService](/capabilities/iot/device-runtime-query)、[DeviceHistoryQueryService](/capabilities/iot/device-history-query)、[DeviceScheduleService](/capabilities/iot/device-scheduling) 和 [DeviceScheduleQueryService](/javadoc/modules/iot/sca4dj-iot-api/com/sca4d/iot/api/schedule/DeviceScheduleQueryService.html) 分别管理当前状态、历史查询和设备定时。
- [DeviceChangePublisher](/javadoc/modules/iot/sca4dj-iot-api/com/sca4d/iot/api/change/DeviceChangePublisher.html) 接收事务提交后的数据区域失效通知。具体应用可以把它转换成浏览器 Realtime 事件。

`sca4dj-iot` 公开 ProductService、ThingModelService、DeviceService、DesiredStateService 和 DeviceCommandService 等 Java Service，供具体应用组合业务流程。自动配置不创建 HTTP 路由。

`DeviceCommandService.cancel(messageId)` 在调用方业务事务中取消未完成的 command。首次取消返回 true，已有终态返回 false。不存在或不是 command 的消息报告无效输入。取消提交后，发布结果写回、ACK 与超时重试都不能恢复该消息。已经进入协议发布的动作可能到达设备，具体应用负责补偿。取消定时规则不会自动取消已经生成的 command。

代码物模型可以在 `ThingCommand` 中使用 `DataKey<T>` 声明命令输入，并通过 `DeviceCommandService` 的 `ThingCommand + DataValues` 入口提交。命令 code、字段 code 和 Java 类型由同一份物模型声明负责。服务仍会读取 Device 当前绑定的物模型，并重新校验完整字段、范围、字符串 `pattern`、ACK 和重试策略。数据库物模型、设备定时等运行时场景继续使用 `String + Map` 入口。这些调用不会伪装成编译期类型化命令。

`DeviceHistoryQueryService.downlinks(Collection<Long>)` 批量读取主库仍保留的下行消息。实现先去重，再按公共 SQL 批次条目和绑定参数限制执行查询。空集合不访问数据库。不存在或已经按保留策略删除的标识不出现在结果中。具体应用在列表中组合下行状态时，应使用 `downlinks(Collection<Long>)`，不按记录逐条查询。

DeviceService 只保存 Product、物模型、协议、公开连接身份和 [DeviceAccessMethod](/javadoc/modules/iot/sca4dj-iot-api/com/sca4d/iot/api/DeviceAccessMethod.html)。它不依赖 IAM 或 Auth，不创建 Subject、Account、Credential 或 Session。匿名接入使用 `ANONYMOUS`（`DeviceAccessMethod.ANONYMOUS_CODE`）。通用 Session 接入使用 `SESSION`（`DeviceAccessMethod.SESSION_CODE`）。具体应用实现 DeviceConnectionAdmission。该实现把协议输入转换为 AuthenticationSubmission 或 Session Token，并组合 Device、Account、Auth 和 Session。

## 模块拥有的数据

主 PostgreSQL 保存业务判断与失败恢复需要的当前事实：

- Product、Product 来源、动态多语言展示字段、允许的认证接入方式和设备协议 code。Product 不保存连接协议白名单。
- 连续发布且不可修改的物模型技术版本，以及可以独立完整替换的路径翻译。
- Device、当前物模型版本、设备协议、`bindingGeneration`、可选直接连接绑定和认证接入方式。
- 默认 PostgreSQL 实现使用的 latest reported 字段组。
- current desired 与可靠下行消息。每条下行只保存最新业务 ACK，不另建 ACK 历史表。
- 设备定时规则、下一次触发时间和最新执行结果。完整执行历史通过事件存储，不另建设备定时执行历史表。

`iot_downlink_message` 是需要跨事务恢复的业务状态，不是临时投递表。它保存 command 或 desired 下行从 `PENDING`、`SENDING`、`WAITING_ACK` 到终态的状态、租约、重试次数、ACK 截止时间和最新 ACK。终态记录按保留期分批删除，`SUCCEEDED`、`FAILED`、`EXPIRED` 与 `SUPERSEDED` 都必须进入清理范围。

Redis 保存可重建的当前连接信息；应用选择 RedisReportedStateStore 时，latest reported 也保存在 Redis 中。没有设备顺序的 reported 使用 IdGenerator 分配固定宽度的平台顺序值，不创建 Redis 递增 key。连接在线结果由 DeviceOnlinePolicy 在查询时计算，不写 PostgreSQL。Redis 丢失不会删除 Product、Device、desired、下行或定时规则。

Device 的 Credential 由通用 Auth 方法模块按 `account_id` 保存。Shared Secret、HMAC 和 X.509 分别属于 `sca4dj-auth-shared-secret`、`sca4dj-auth-hmac` 和 `sca4dj-auth-x509`。IoT 主库没有 Secret 或 Certificate 表。

具体应用自己的 Product/Device 启停 Profile、HTTP 幂等记录、权限和展示字段不属于 `sca4dj-iot` 模块。

## 消息与历史的范围

IoT 使用通用 [MessageBus](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/messaging/MessageBus.html) 与 `DispatchPolicy`，不创建 IoT 专用 Outbox：

- Telemetry 在 reported 主事务提交后使用 `DispatchPolicy.afterCommit()`。它不写 Outbox。Broker 发布失败可能丢失这一批历史样本，但不会回滚 latest reported。
- 物模型事件按 ThingEvent 的 delivery 声明发布。`RELIABLE` 在主事务写通用 `messaging_outbox`，其他事件在提交后直接发布。
- 下行发送、ACK 超时、断连、每个首次接受的 ACK 阶段和设备定时执行结果必须可靠发布，统一使用 `messaging_outbox`。

启用 `sca4dj-iot-timescale` 后，消息 Consumer 直接把 Telemetry 写入 `iot_telemetry_sample`，并把物模型事件、ACK 和设备定时执行结果写入 `iot_event`。TimescaleDB 不参与 IoT 主事务，也没有高频 Outbox、通信日志、ACK 历史或定时执行专表。

## 允许依赖

`sca4dj-iot` 直接依赖 `sca4dj-iot-api` 和 Core Spring，不依赖 IAM、Auth、EMQX 或 TimescaleDB 模块。协议适配器只依赖 IoT API，通过公开接口提交上行、发布下行和报告连接，不反向读取 IoT 基础模块的 Repository 或 Mapper。

具体应用可以同时依赖 IoT、IAM、Auth 及认证方法模块，并在具体应用的 Service 中建立 Device Subject、固定 Realm Account、Credential、Session 和 Connection。把这些组合写在具体应用中属于业务选择，不应把认证方法分支重新放入 DeviceService。

## 启用条件

加入 `sca4dj-iot` 后，自动配置装配 MyBatis Repository、领域 Service、Redis 连接信息读写和上行入口。运行应用还必须提供以下条件：

- Core Spring、主 PostgreSQL、Redis、IdGenerator 和统一 JSON ObjectMapper。
- 通用 Messaging 的 MessageBus，用于提交后消息和可靠消息。
- 至少一个符合 Product 声明的 DeviceProtocol。
- 发送下行与处理 ACK 超时的 MessageConsumer 运行环境。
- 需要历史查询时提供一个 DeviceHistoryStore，例如独立的 `sca4dj-iot-timescale`。

模块结构片段由应用的 Flyway 初始迁移收集，已生成的迁移由应用继续维护。Timescale 模块使用自己的 DataSource 和 Flyway，不拼入应用主库 V1。

## 示例

以下示例对应温控器（Thermostat）的目标温度命令。应用先检查操作人的权限，再提交 command，并保存返回的消息标识。命令取消时使用同一标识。

```java
import com.sca4d.iot.message.DeviceCommandService;
import com.sca4d.iot.api.message.DeviceDownlink;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ThermostatCommandService {

    private final DeviceCommandService commands;

    public ThermostatCommandService(DeviceCommandService commands) {
        this.commands = commands;
    }

    @Transactional
    public long setTargetTemperature(long thermostatId, long targetCelsius) {
        // 应用代码：检查操作人是否有权限控制该温控器。
        DeviceDownlink downlink = commands.submit(
                thermostatId,
                "setTargetTemperature",
                Map.of("targetCelsius", targetCelsius),
                null,
                Duration.ofSeconds(30));
        return downlink.messageId();
    }

    @Transactional
    public boolean cancel(long messageId) {
        return commands.cancel(messageId);
    }
}
```

`submit` 的第四个参数为可空的有效期截止时间，传入 `null` 表示不设置截止时间。第五个参数为 ACK 等待时间。`cancel` 返回 false 时，消息已经处于终态，调用方不得据此认为设备没有执行。

## 失败时哪些数据不会改变

- 物模型只有在全部声明协议、属性策略和命令策略通过校验后才发布。
- Device 重新绑定要求具体应用先停用设备，并且旧 binding generation 没有活动下行消息。
- reported、desired、ACK 与下行状态在主事务内改变。协议传输、消息代理和 TimescaleDB 不加入主事务。
- Telemetry 提交后发布失败不会回滚 latest reported，也没有主库重试副本。
- 可靠事件、下行、ACK 超时和断连的 Broker 发布失败会留在通用 Messaging Outbox 中重试。
- 下行 Consumer 使用数据库租约。传输失败、ACK 超时与业务拒绝由消息快照中的 RetryPolicy 决定重试或进入终态。
- 设备定时扫描 PostgreSQL 的 `next_fire_at`，每次查询一条到期候选规则。取得 Redis 分布式锁后，在独立事务内重新检查 `version`、状态和计划时间。定时不使用显式行锁，也不为每条规则创建线程或 Redis 定时项。到期时只生成可靠 command 下行，实际发送由下行消息 Consumer 完成。
- 模块使用 JobSchedule 声明设备定时扫描和终态下行清理。具体应用只配置触发间隔。这些 Job 不记录 Job Run。
- Session 终止、Redis 中的连接信息和 Broker 断连不在同一个跨存储事务中。具体应用可靠发布带明确连接代次的断连消息。协议 Consumer 重复处理这条断连消息时，不能断开之后建立的新连接。
- 模块不判断谁能创建 Product、启用 Device、修改 desired 或提交 command。具体应用必须在公开入口外层完成权限和状态检查。

EMQX 怎样把 MQTT 连接和报文转换成这些对象，见 [EMQX 设备接入模块](/capabilities/iot/emqx)。两张高频历史表怎样装配，见 [IoT TimescaleDB 历史模块](/capabilities/iot/timescale)。

IoT 领域 RetryPolicy 保留设备消息和失败码判断。默认命令与 desired 同步策略复用 Core 的 [RetryRule](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/retry/RetryRule.html) 和 [Backoff](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/retry/Backoff.html)。ACK、取消、过期，以及 reported 与 desired 是否一致的判断，仍由下行状态机处理，不使用同步执行器等待设备回执。
## 扩展方式

具体应用通过以下 Spring Bean 扩展 IoT 基础模块：

- **DeviceConnectionAdmission**：实现连接准入，把协议输入转换为认证输入并组合 Device、Account、Auth 和 Session。应用必须提供该 Bean。
- **ReportedStateStore**：替换 latest reported 的存储。自动配置在未提供时使用 PostgreSQL 默认实现。
- **DeviceOnlinePolicy**：替换在线判定。自动配置在未提供时使用 `ConnectionPresentDeviceOnlinePolicy`。
- **DeviceUplinkReceiver**：替换上行接收逻辑。自动配置在未提供时使用默认 Receiver。
- **DeviceDispatchAdmission**：控制每次发送尝试的准入。未提供时直接允许发送。
- **DeviceHistoryStore**：提供遥测、事件、ACK 与设备定时执行历史。未提供时，历史查询返回依赖不可用错误。
- **DeviceProtocol**：每个协议实现登记为 Bean。DeviceProtocolCatalog 接收全部协议 Bean，并按 code 查找。

模块的默认实现使用 `@ConditionalOnMissingBean` 或可选依赖注入。具体应用提供同类型 Bean 后，默认实现不再装配。
