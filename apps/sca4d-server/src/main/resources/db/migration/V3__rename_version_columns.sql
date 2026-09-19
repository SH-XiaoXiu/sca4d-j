ALTER TABLE iam_principal
    RENAME COLUMN roles_revision TO roles_version;

ALTER TABLE job.job_run
    RENAME COLUMN job_revision TO job_definition_version;

ALTER TABLE iot_device
    RENAME COLUMN binding_revision TO binding_generation;
ALTER TABLE iot_device
    RENAME COLUMN desired_revision TO desired_sequence;

ALTER TABLE iot_reported_group
    RENAME COLUMN binding_revision TO binding_generation;
ALTER TABLE iot_reported_group
    RENAME COLUMN revision TO version;

ALTER TABLE iot_desired_property
    RENAME COLUMN binding_revision TO binding_generation;
ALTER TABLE iot_desired_property
    RENAME COLUMN desired_revision TO desired_sequence;

ALTER TABLE iot_downlink_message
    RENAME COLUMN binding_revision TO binding_generation;
ALTER TABLE iot_downlink_message
    RENAME COLUMN desired_revision TO desired_sequence;

COMMENT ON COLUMN iam_principal.roles_version IS 'Principal 角色集合版本';
COMMENT ON COLUMN job.job_run.job_definition_version IS '本次运行采用的 Job 定义版本';
COMMENT ON COLUMN iot_device.binding_generation IS '物模型或协议每次重新绑定后递增的代数';
COMMENT ON COLUMN iot_device.desired_sequence IS '当前绑定内最近分配的 desired 递增序号';
COMMENT ON COLUMN iot_reported_group.binding_generation IS '状态所属 Device 绑定代数';
COMMENT ON COLUMN iot_reported_group.version IS '每次接受新状态后递增的乐观锁版本';
COMMENT ON COLUMN iot_desired_property.binding_generation IS 'desired 属性所属 Device 绑定代数';
COMMENT ON COLUMN iot_desired_property.desired_sequence IS 'Device 内生成该期望值时分配的递增序号';
COMMENT ON COLUMN iot_downlink_message.binding_generation IS '创建消息时的 Device 绑定代数';
COMMENT ON COLUMN iot_downlink_message.desired_sequence IS 'DESIRED 消息对应的期望序号，COMMAND 时为空';
