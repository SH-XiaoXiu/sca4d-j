ALTER TABLE iot_telemetry_sample
    RENAME COLUMN binding_revision TO binding_generation;

ALTER TABLE iot_event
    RENAME COLUMN binding_revision TO binding_generation;

COMMENT ON COLUMN iot_telemetry_sample.binding_generation IS '样本产生时的 Device 绑定代数快照';
COMMENT ON COLUMN iot_event.binding_generation IS '事件发生时的 Device 绑定代数快照';
