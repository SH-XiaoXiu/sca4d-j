# IoT TimescaleDB 历史模块

源码模块：`modules/iot/sca4dj-iot-timescale`

`sca4dj-iot-timescale` 在 `sca4dj.iot.timescale.enabled=true` 时装配历史库专用的 SqlSessionFactory、SqlSessionTemplate、消息 Consumer、DeviceHistoryStore 和清理任务。具体应用通过动态数据源配置提供 `iot-history` 路由，并负责历史库迁移。

模块只提供无版本的完整结构 `META-INF/sca4dj/timescale/schema.sql`，不维护 V1、V2 等迁移文件，也不在启动时自动执行建表 SQL。具体应用将这份结构纳入历史库自己的初始化迁移。结构更新后，已有数据库由应用新增向前迁移。历史库结构与主库迁移分开维护。

## Bean 与事务

MyBatis 会话对象和 TimescaleMapper 使用 [IotTimescale](/capabilities/iot/timescale-configuration) 限定，并声明为非默认候选。Consumer、历史查询与清理任务通过 `@DS(IotTimescale.DATASOURCE)` 选择历史库，并使用 `REQUIRES_NEW` 开启独立事务。主库和历史库共用动态路由 DataSource 与事务管理器。

## 两张表

`iot_telemetry_sample` 按 `observed_at` 建立 hypertable，使用 `(observed_at, message_id, sample_index)` 主键处理消息重复投递。`iot_event` 按 `occurred_at` 建立 hypertable，使用 `(occurred_at, message_id)` 主键。`iot_event` 的来源事件唯一索引包含分区时间，因为 TimescaleDB 的 hypertable 唯一索引必须包含全部分区列。

TelemetryConsumer 消费事务提交后的消息，并直接批量写入 `iot_telemetry_sample`。EventConsumer 消费物模型事件、ACK 阶段和设备定时执行事件，并写入 `iot_event`。模块没有 IoT 专用 Outbox、ACK 表、定时执行表或通信日志表。

ACK 事件的 `message_id` 是通用 Messaging 分配的事件消息标识。关联的下行消息标识保存在 `data_json.messageId` 中。按下行查询回执时使用 `data_json.messageId`。`iot_event` 上对应的部分索引只包含 `$downlink.ack` 事件，并按接收时间、发生时间和事件消息标识排序。

## 示例

以下配置启用历史库，并设置保留期与清理间隔。历史库连接本身由动态数据源配置提供，见 [IotTimescale](/capabilities/iot/timescale-configuration)：

```yaml
sca4dj:
  iot:
    timescale:
      enabled: true
      cleanup-interval: PT1H
      telemetry-retention: P90D
      event-retention: P90D
      max-query-results: 1000
```

`telemetry-retention` 与 `event-retention` 都必须为正数。`max-query-results` 的可配置范围为 1 到 10000。

## 保留与失败

默认 Telemetry 和事件保留期都是 90 天。模块提供无记录的 `JobSchedule`。具体应用在 `sca4dj.iot.timescale.cleanup-interval` 中声明清理间隔，默认一小时。清理计划按该间隔清理 chunk，且不创建 Job Run。保留期必须为正数。清理只删除早于截止时间的完整 chunk。

TimescaleDB 写入不参与主 PostgreSQL 事务。Telemetry 发布失败可能丢失历史样本。可靠事件已经由通用 Messaging Outbox 保证 Broker 发布。Consumer 写入失败由消息消费重试处理。
## 扩展方式

应用通过以下方式启用历史库：

1. 在动态数据源中声明 `iot-history` 连接。
2. 把模块提供的 `META-INF/sca4dj/timescale/schema.sql` 纳入历史库初始化迁移，结构变化时新增向前迁移。
3. 设置 `sca4dj.iot.timescale.enabled=true` 及保留期配置。

模块提供 `TimescaleDeviceHistoryStore`，IoT 历史查询通过它读取 Telemetry、事件和 ACK。应用不替换该实现，需要不同的查询结果时在应用层转换。
