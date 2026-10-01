CREATE TABLE iot_product (
    id BIGINT PRIMARY KEY,
    code VARCHAR(128) NOT NULL UNIQUE,
    i18n JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE iot_product_access_method (
    product_id BIGINT NOT NULL,
    method_code VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (product_id, method_code)
);

CREATE TABLE iot_product_protocol (
    product_id BIGINT NOT NULL,
    protocol_code VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (product_id, protocol_code)
);

CREATE TABLE iot_thing_model (
    product_id BIGINT NOT NULL,
    version INTEGER NOT NULL,
    model_json JSONB NOT NULL,
    content_hash CHAR(64) NOT NULL,
    i18n JSONB NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (product_id, version)
);

CREATE TABLE iot_device (
    id BIGINT PRIMARY KEY,
    product_source VARCHAR(16) NOT NULL,
    product_code VARCHAR(128) NOT NULL,
    product_id BIGINT,
    code VARCHAR(128) NOT NULL,
    serial_number VARCHAR(256),
    display_name VARCHAR(128) NOT NULL,
    remark VARCHAR(4096),
    thing_model_version INTEGER NOT NULL,
    binding_generation BIGINT NOT NULL DEFAULT 1,
    protocol_code VARCHAR(128) NOT NULL,
    connection_protocol VARCHAR(128),
    connection_identifier_type VARCHAR(128),
    connection_identifier_value VARCHAR(512),
    desired_sequence BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_iot_device_product_code_code UNIQUE (product_code, code),
    CONSTRAINT uq_iot_device_product_code_serial UNIQUE (product_code, serial_number),
    CONSTRAINT uq_iot_device_connection_identifier
        UNIQUE (connection_protocol, connection_identifier_type, connection_identifier_value)
);

CREATE TABLE iot_device_access_method (
    device_id BIGINT NOT NULL,
    method_code VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (device_id, method_code)
);

CREATE INDEX idx_iot_device_product ON iot_device (product_id, id);

CREATE TABLE iot_reported_group (
    device_id BIGINT NOT NULL,
    binding_generation BIGINT NOT NULL,
    group_code VARCHAR(128) NOT NULL,
    order_scope VARCHAR(128) NOT NULL,
    order_value VARCHAR(256) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    state_json JSONB NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (device_id, binding_generation, group_code)
);

CREATE TABLE iot_desired_property (
    device_id BIGINT NOT NULL,
    binding_generation BIGINT NOT NULL,
    property_code VARCHAR(128) NOT NULL,
    desired_sequence BIGINT NOT NULL,
    value_json JSONB NOT NULL,
    converged_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (device_id, binding_generation, property_code)
);

CREATE TABLE iot_downlink_message (
    message_id BIGINT PRIMARY KEY,
    device_id BIGINT NOT NULL,
    binding_generation BIGINT NOT NULL,
    kind VARCHAR(16) NOT NULL,
    target_code VARCHAR(128) NOT NULL,
    desired_sequence BIGINT,
    schedule_id BIGINT,
    scheduled_at TIMESTAMP WITH TIME ZONE,
    data_json JSONB NOT NULL,
    acknowledgement_policy VARCHAR(128) NOT NULL,
    acknowledgement_timeout_millis BIGINT NOT NULL,
    retry_policy VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    ack_deadline TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    last_ack_identity_hash CHAR(64),
    last_ack_code VARCHAR(128),
    last_ack_data_json JSONB,
    last_ack_source VARCHAR(32),
    last_ack_source_acknowledgement_id VARCHAR(256),
    last_acknowledged_at TIMESTAMP WITH TIME ZONE,
    last_ack_received_at TIMESTAMP WITH TIME ZONE,
    last_failure_code VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_iot_downlink_device_page
    ON iot_downlink_message (device_id, created_at DESC, message_id DESC);
CREATE INDEX idx_iot_downlink_completed_cleanup
    ON iot_downlink_message (completed_at, message_id)
    WHERE status IN ('SUCCEEDED', 'FAILED', 'SUPERSEDED', 'CANCELLED', 'EXPIRED');

CREATE TABLE iot_device_schedule (
    schedule_id BIGINT PRIMARY KEY,
    device_id BIGINT NOT NULL,
    command_code VARCHAR(128) NOT NULL,
    command_inputs_json JSONB NOT NULL,
    trigger_type VARCHAR(16) NOT NULL,
    execute_at TIMESTAMP WITH TIME ZONE,
    cron_expression VARCHAR(256),
    zone_id VARCHAR(64),
    valid_for_millis BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    next_fire_at TIMESTAMP WITH TIME ZONE,
    last_planned_at TIMESTAMP WITH TIME ZONE,
    last_triggered_at TIMESTAMP WITH TIME ZONE,
    last_execution_status VARCHAR(16),
    last_message_id BIGINT,
    last_failure_code VARCHAR(128),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_iot_device_schedule_page ON iot_device_schedule (device_id, schedule_id DESC);
CREATE INDEX idx_iot_device_schedule_due
    ON iot_device_schedule (next_fire_at, schedule_id) WHERE status = 'ACTIVE';

COMMENT ON TABLE iot_product IS '定义一类数据库 Product 允许采用的认证接入方式和设备协议';
COMMENT ON COLUMN iot_product.id IS '数据库 Product 全局标识';
COMMENT ON COLUMN iot_product.code IS '数据库 Product 的全局稳定编码';
COMMENT ON COLUMN iot_product.i18n IS '按 BCP 47 Locale 保存的 Product 完整多语言展示字段';
COMMENT ON COLUMN iot_product.created_at IS 'Product 创建时间';
COMMENT ON COLUMN iot_product.updated_at IS 'Product 最近更新时间，由数据库触发器维护';

COMMENT ON TABLE iot_product_access_method IS '记录数据库 Product 允许 Device 采用的认证接入方式';
COMMENT ON COLUMN iot_product_access_method.product_id IS '接入方式所属数据库 Product 标识';
COMMENT ON COLUMN iot_product_access_method.method_code IS 'Product 允许的接入方式编码，例如 ANONYMOUS、SESSION 或 ACCOUNT:AUTHENTICATION/SHARED_SECRET';
COMMENT ON COLUMN iot_product_access_method.created_at IS 'Product 接入方式关系创建时间';

COMMENT ON TABLE iot_product_protocol IS '记录数据库 Product 允许 Device 绑定的协议实现';
COMMENT ON COLUMN iot_product_protocol.product_id IS '协议所属数据库 Product 标识';
COMMENT ON COLUMN iot_product_protocol.protocol_code IS 'Product 允许的 DeviceProtocol 编码';
COMMENT ON COLUMN iot_product_protocol.created_at IS 'Product 协议关系创建时间';

COMMENT ON TABLE iot_thing_model IS '保存数据库 Product 下连续发布且不可修改的物模型版本';
COMMENT ON COLUMN iot_thing_model.product_id IS '物模型所属数据库 Product 标识';
COMMENT ON COLUMN iot_thing_model.version IS 'Product 内从 1 开始连续递增的物模型版本';
COMMENT ON COLUMN iot_thing_model.model_json IS '完整物模型定义的 JSON 对象';
COMMENT ON COLUMN iot_thing_model.content_hash IS '规范化物模型 JSON 的 SHA-256 小写十六进制摘要';
COMMENT ON COLUMN iot_thing_model.i18n IS '按 BCP 47 Locale 和稳定展示路径保存的完整动态翻译';
COMMENT ON COLUMN iot_thing_model.published_at IS '该物模型版本完成发布的时间';

COMMENT ON TABLE iot_device IS '保存 DEVICE Subject 对应的公共 IoT Profile 和可选直接连接绑定';
COMMENT ON COLUMN iot_device.id IS 'Device 标识，与对应 DEVICE Subject 标识相同';
COMMENT ON COLUMN iot_device.product_source IS 'Product 定义来源，DATABASE 使用 product_id，CODE 由应用目录解析';
COMMENT ON COLUMN iot_device.product_code IS 'Device 永久保存的 Product 稳定编码';
COMMENT ON COLUMN iot_device.product_id IS '数据库 Product 标识；代码 Product 为空';
COMMENT ON COLUMN iot_device.code IS '同一 Product 内唯一的 Device 编码';
COMMENT ON COLUMN iot_device.serial_number IS '同一 Product 内可选且唯一的设备序列号';
COMMENT ON COLUMN iot_device.display_name IS 'Device 默认展示名称';
COMMENT ON COLUMN iot_device.remark IS 'Device 可选备注';
COMMENT ON COLUMN iot_device.thing_model_version IS 'Device 当前绑定的已发布物模型版本';
COMMENT ON COLUMN iot_device.binding_generation IS '物模型或协议每次重新绑定后递增的版本';
COMMENT ON COLUMN iot_device.protocol_code IS 'Device 当前绑定的 DeviceProtocol 编码';
COMMENT ON COLUMN iot_device.connection_protocol IS 'Device 可选直接连接使用的协议编码';
COMMENT ON COLUMN iot_device.connection_identifier_type IS 'Device 可选直接连接使用的公开身份类型';
COMMENT ON COLUMN iot_device.connection_identifier_value IS '连接协议和身份类型范围内唯一的公开 Device 身份值';
COMMENT ON COLUMN iot_device.desired_sequence IS '当前绑定内最近分配的 desired 递增版本';
COMMENT ON COLUMN iot_device.created_at IS 'Device 创建时间';
COMMENT ON COLUMN iot_device.updated_at IS 'Device 最近更新时间，由数据库触发器维护';

COMMENT ON TABLE iot_device_access_method IS '记录 Device 当前启用的认证接入方式';
COMMENT ON COLUMN iot_device_access_method.device_id IS '接入方式所属 Device 标识';
COMMENT ON COLUMN iot_device_access_method.method_code IS 'Device 当前启用的接入方式编码，例如 ANONYMOUS、SESSION 或 ACCOUNT:AUTHENTICATION/SHARED_SECRET';
COMMENT ON COLUMN iot_device_access_method.created_at IS 'Device 接入方式关系创建时间';

COMMENT ON TABLE iot_reported_group IS '保存每个 reported 字段组最后接受的顺序和值';
COMMENT ON COLUMN iot_reported_group.device_id IS 'reported 状态所属 Device 标识';
COMMENT ON COLUMN iot_reported_group.binding_generation IS '状态所属 Device 绑定版本';
COMMENT ON COLUMN iot_reported_group.group_code IS '物模型中定义的 reported 字段组编码';
COMMENT ON COLUMN iot_reported_group.order_scope IS '解释 order_value 所需的排序范围编码';
COMMENT ON COLUMN iot_reported_group.order_value IS '协议提供且在 order_scope 内可比较的顺序值';
COMMENT ON COLUMN iot_reported_group.content_hash IS '规范化 state_json 的 SHA-256 小写十六进制摘要';
COMMENT ON COLUMN iot_reported_group.state_json IS '该字段组最近一次接受的完整 reported 值';
COMMENT ON COLUMN iot_reported_group.observed_at IS 'Device 观察到该组状态的时间';
COMMENT ON COLUMN iot_reported_group.received_at IS '平台收到该组状态的时间';
COMMENT ON COLUMN iot_reported_group.version IS '每次接受新状态后递增的乐观锁版本';
COMMENT ON COLUMN iot_reported_group.created_at IS '该绑定内字段组首个状态创建时间';
COMMENT ON COLUMN iot_reported_group.updated_at IS '字段组最近接受状态的时间，由数据库触发器维护';

COMMENT ON TABLE iot_desired_property IS '保存平台持续期望的当前属性值和收敛结果';
COMMENT ON COLUMN iot_desired_property.device_id IS 'desired 属性所属 Device 标识';
COMMENT ON COLUMN iot_desired_property.binding_generation IS 'desired 属性所属 Device 绑定版本';
COMMENT ON COLUMN iot_desired_property.property_code IS '物模型中可写属性的稳定编码';
COMMENT ON COLUMN iot_desired_property.desired_sequence IS 'Device 内生成该期望值时分配的递增版本';
COMMENT ON COLUMN iot_desired_property.value_json IS '平台当前持续期望的属性值';
COMMENT ON COLUMN iot_desired_property.converged_at IS 'reported 值确认已达到当前期望值的时间';
COMMENT ON COLUMN iot_desired_property.created_at IS '该绑定内属性首次建立期望值的时间';
COMMENT ON COLUMN iot_desired_property.updated_at IS '期望值或收敛结果最近更新时间，由数据库触发器维护';

COMMENT ON TABLE iot_downlink_message IS '保存 desired 和 command 共用的可靠下行状态机';
COMMENT ON COLUMN iot_downlink_message.message_id IS '下行消息全局标识';
COMMENT ON COLUMN iot_downlink_message.device_id IS '接收下行的 Device 标识';
COMMENT ON COLUMN iot_downlink_message.binding_generation IS '创建消息时的 Device 绑定版本';
COMMENT ON COLUMN iot_downlink_message.kind IS '消息种类，DESIRED 表示期望属性，COMMAND 表示一次性命令';
COMMENT ON COLUMN iot_downlink_message.target_code IS '目标属性或命令的物模型编码';
COMMENT ON COLUMN iot_downlink_message.desired_sequence IS 'DESIRED 消息对应的期望版本，COMMAND 时为空';
COMMENT ON COLUMN iot_downlink_message.schedule_id IS '生成该 command 的设备定时规则标识，即时下行为空';
COMMENT ON COLUMN iot_downlink_message.scheduled_at IS '设备定时规则本次原定触发时间，即时下行为空';
COMMENT ON COLUMN iot_downlink_message.data_json IS '协议编码前的属性值或命令输入';
COMMENT ON COLUMN iot_downlink_message.acknowledgement_policy IS '决定发送后是否及怎样等待 Device ACK 的策略编码';
COMMENT ON COLUMN iot_downlink_message.acknowledgement_timeout_millis IS '创建消息时快照的 ACK 等待时间，单位为毫秒';
COMMENT ON COLUMN iot_downlink_message.retry_policy IS '决定失败和超时后重试时间的策略编码';
COMMENT ON COLUMN iot_downlink_message.status IS '可靠下行状态机的当前状态';
COMMENT ON COLUMN iot_downlink_message.attempt_count IS '已经开始的发送尝试次数';
COMMENT ON COLUMN iot_downlink_message.ack_deadline IS '最近一次发布后等待 Device ACK 的截止时间';
COMMENT ON COLUMN iot_downlink_message.expires_at IS '消息不再允许发送或重试的时间';
COMMENT ON COLUMN iot_downlink_message.last_ack_identity_hash IS '最新 ACK 来源事实标识的 SHA-256，用于忽略当前阶段的重复投递';
COMMENT ON COLUMN iot_downlink_message.last_ack_code IS '协议或 reported 推导出的最新 ACK 阶段编码';
COMMENT ON COLUMN iot_downlink_message.last_ack_data_json IS '最新 ACK 阶段的结构化数据';
COMMENT ON COLUMN iot_downlink_message.last_ack_source IS '最新 ACK 的接入来源编码';
COMMENT ON COLUMN iot_downlink_message.last_ack_source_acknowledgement_id IS '来源提供的最新 ACK 稳定事实标识';
COMMENT ON COLUMN iot_downlink_message.last_acknowledged_at IS '最新 ACK 表示的 Device 处理时间';
COMMENT ON COLUMN iot_downlink_message.last_ack_received_at IS '平台收到最新 ACK 的时间';
COMMENT ON COLUMN iot_downlink_message.last_failure_code IS '最近一次发送失败或停止重试的稳定 code';
COMMENT ON COLUMN iot_downlink_message.created_at IS '下行消息创建时间';
COMMENT ON COLUMN iot_downlink_message.updated_at IS '下行状态最近更新时间，由数据库触发器维护';
COMMENT ON COLUMN iot_downlink_message.completed_at IS '下行进入最终状态的时间';
COMMENT ON COLUMN iot_downlink_message.version IS '下行状态并发修改使用的乐观版本';

COMMENT ON TABLE iot_device_schedule IS '保存到期后生成设备 command 下行的单次或 Cron 规则';
COMMENT ON COLUMN iot_device_schedule.schedule_id IS 'Device 计划全局标识';
COMMENT ON COLUMN iot_device_schedule.device_id IS '执行计划的 Device 标识';
COMMENT ON COLUMN iot_device_schedule.command_code IS '到期时生成的物模型命令编码';
COMMENT ON COLUMN iot_device_schedule.command_inputs_json IS '生成命令下行时使用的输入';
COMMENT ON COLUMN iot_device_schedule.trigger_type IS '单次时间或 Cron 的触发类型';
COMMENT ON COLUMN iot_device_schedule.execute_at IS '单次计划的执行时间，Cron 计划为空';
COMMENT ON COLUMN iot_device_schedule.cron_expression IS 'Cron 计划表达式，单次计划为空';
COMMENT ON COLUMN iot_device_schedule.zone_id IS '解释 Cron 表达式使用的时区标识';
COMMENT ON COLUMN iot_device_schedule.valid_for_millis IS '每次生成命令从计划时间起允许延迟执行的毫秒数';
COMMENT ON COLUMN iot_device_schedule.status IS '计划当前是否可继续触发';
COMMENT ON COLUMN iot_device_schedule.next_fire_at IS '调度器下一次应处理该计划的时间';
COMMENT ON COLUMN iot_device_schedule.last_planned_at IS '最近一次执行结果对应的原定触发时间';
COMMENT ON COLUMN iot_device_schedule.last_triggered_at IS '最近一次执行结果的实际处理时间';
COMMENT ON COLUMN iot_device_schedule.last_execution_status IS '最近一次执行生成下行、过期或失败的结果';
COMMENT ON COLUMN iot_device_schedule.last_message_id IS '最近一次成功生成的下行消息标识';
COMMENT ON COLUMN iot_device_schedule.last_failure_code IS '最近一次业务失败的稳定 code';
COMMENT ON COLUMN iot_device_schedule.version IS '计划并发修改使用的乐观版本';
COMMENT ON COLUMN iot_device_schedule.created_at IS '计划创建时间';
COMMENT ON COLUMN iot_device_schedule.updated_at IS '计划最近更新时间，由数据库触发器维护';

CREATE TRIGGER set_iot_product_updated_at
    BEFORE UPDATE ON iot_product
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

CREATE TRIGGER set_iot_device_updated_at
    BEFORE UPDATE ON iot_device
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

CREATE TRIGGER set_iot_reported_group_updated_at
    BEFORE UPDATE ON iot_reported_group
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

CREATE TRIGGER set_iot_desired_property_updated_at
    BEFORE UPDATE ON iot_desired_property
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

CREATE TRIGGER set_iot_downlink_message_updated_at
    BEFORE UPDATE ON iot_downlink_message
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

CREATE TRIGGER set_iot_device_schedule_updated_at
    BEFORE UPDATE ON iot_device_schedule
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();
