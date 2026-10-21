-- 独立 IoT 历史库的完整结构；由具体应用纳入该数据源的迁移。
CREATE EXTENSION IF NOT EXISTS timescaledb;

CREATE TABLE iot_telemetry_sample (
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    message_id BIGINT NOT NULL,
    sample_index INTEGER NOT NULL,
    device_id BIGINT NOT NULL,
    binding_generation BIGINT NOT NULL,
    property_code VARCHAR(128) NOT NULL,
    value_json JSONB NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (observed_at, message_id, sample_index)
);

SELECT create_hypertable('iot_telemetry_sample', by_range('observed_at'), if_not_exists => TRUE);
CREATE INDEX idx_iot_telemetry_device_property
    ON iot_telemetry_sample (device_id, property_code, observed_at DESC);

CREATE TABLE iot_event (
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    message_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    binding_generation BIGINT NOT NULL,
    event_code VARCHAR(128) NOT NULL,
    source_event_id VARCHAR(256),
    data_json JSONB NOT NULL,
    device_occurred_at BOOLEAN NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (occurred_at, message_id)
);

SELECT create_hypertable('iot_event', by_range('occurred_at'), if_not_exists => TRUE);
CREATE UNIQUE INDEX uq_iot_event_source
    ON iot_event (occurred_at, device_id, binding_generation, event_code, source_event_id)
    WHERE source_event_id IS NOT NULL;
CREATE INDEX idx_iot_event_device
    ON iot_event (device_id, occurred_at DESC, message_id DESC);
CREATE INDEX idx_iot_event_schedule
    ON iot_event (((data_json ->> 'scheduleId')::BIGINT), occurred_at DESC)
    WHERE event_code = '$schedule.execution';

-- 回执关联的下行标识保存在事件数据中；表内 message_id 是历史事件的消息标识。
CREATE INDEX idx_iot_event_acknowledgement_downlink
    ON iot_event (((data_json ->> 'messageId')::BIGINT), received_at DESC, occurred_at DESC, message_id DESC)
    WHERE event_code = '$downlink.ack';

COMMENT ON TABLE iot_telemetry_sample IS '保存 MessageConsumer 从 reported 消息写入的 Device Telemetry 样本';
COMMENT ON COLUMN iot_telemetry_sample.observed_at IS 'Device 观察到属性值的时间和 hypertable 分区时间';
COMMENT ON COLUMN iot_telemetry_sample.message_id IS '通用 Messaging 分配的稳定消息标识';
COMMENT ON COLUMN iot_telemetry_sample.sample_index IS '样本在同一消息内从 0 开始的稳定顺序';
COMMENT ON COLUMN iot_telemetry_sample.device_id IS '产生样本的 Device 标识快照';
COMMENT ON COLUMN iot_telemetry_sample.binding_generation IS '样本产生时的 Device 绑定代数快照';
COMMENT ON COLUMN iot_telemetry_sample.property_code IS '物模型中声明为 Telemetry 的属性编码';
COMMENT ON COLUMN iot_telemetry_sample.value_json IS '已经通过物模型校验的属性值';
COMMENT ON COLUMN iot_telemetry_sample.received_at IS '平台收到 reported 样本的时间';

COMMENT ON TABLE iot_event IS '保存 MessageConsumer 写入的物模型事件和 IoT 状态机事件';
COMMENT ON COLUMN iot_event.occurred_at IS '事件发生时间和 hypertable 分区时间';
COMMENT ON COLUMN iot_event.message_id IS '通用 Messaging 分配的稳定消息标识';
COMMENT ON COLUMN iot_event.device_id IS '产生事件的 Device 标识快照';
COMMENT ON COLUMN iot_event.binding_generation IS '事件发生时的 Device 绑定代数快照';
COMMENT ON COLUMN iot_event.event_code IS '物模型事件或 IoT 状态机事件编码';
COMMENT ON COLUMN iot_event.source_event_id IS 'Device 或 IoT 状态机提供的稳定事实标识；只在相同 occurred_at 分区键内约束唯一';
COMMENT ON COLUMN iot_event.data_json IS '已经校验且不包含认证凭据的结构化事件数据';
COMMENT ON COLUMN iot_event.device_occurred_at IS 'occurred_at 是否由 Device 或稳定来源提供';
COMMENT ON COLUMN iot_event.received_at IS '平台收到或产生事件的时间';
