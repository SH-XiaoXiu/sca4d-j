CREATE TABLE messaging_outbox (
    outbox_id BIGINT PRIMARY KEY,
    message_id BIGINT NOT NULL,
    target_handler_code VARCHAR(128),
    handler_attempt BIGINT,
    handler_retry_rule JSONB,
    message_code VARCHAR(128) NOT NULL,
    envelope BYTEA NOT NULL,
    status VARCHAR(16) NOT NULL,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempt_count BIGINT NOT NULL DEFAULT 0,
    lease_id VARCHAR(64),
    lease_until TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_messaging_outbox_message ON messaging_outbox (message_id)
    WHERE target_handler_code IS NULL;
CREATE UNIQUE INDEX uk_messaging_outbox_handler_attempt
    ON messaging_outbox (target_handler_code, message_id, handler_attempt)
    WHERE target_handler_code IS NOT NULL;

CREATE INDEX idx_messaging_outbox_dispatch
    ON messaging_outbox (available_at, outbox_id)
    WHERE status IN ('PENDING', 'FAILED');

CREATE INDEX idx_messaging_outbox_lease
    ON messaging_outbox (lease_until, outbox_id)
    WHERE status = 'SENDING';

CREATE INDEX idx_messaging_outbox_sent
    ON messaging_outbox (sent_at, outbox_id)
    WHERE status = 'SENT';

COMMENT ON TABLE messaging_outbox IS '保存业务事务中登记且尚未由 RabbitMQ 确认发布的消息信封';
COMMENT ON COLUMN messaging_outbox.message_id IS '消息信封全局标识';
COMMENT ON COLUMN messaging_outbox.message_code IS '消费者选择消息类型使用的稳定消息编码';
COMMENT ON COLUMN messaging_outbox.envelope IS '已经校验并序列化的 RabbitMQ 业务消息信封';
COMMENT ON COLUMN messaging_outbox.status IS 'PENDING、SENDING、FAILED 或 SENT，不包含发布端 DEAD 状态';
COMMENT ON COLUMN messaging_outbox.available_at IS '发布 Worker 下一次允许领取的时间';
COMMENT ON COLUMN messaging_outbox.attempt_count IS '已经开始的发布尝试次数';
COMMENT ON COLUMN messaging_outbox.lease_id IS '当前发布租约标识，未领取时为空';
COMMENT ON COLUMN messaging_outbox.lease_until IS '当前发布租约截止时间，未领取时为空';
COMMENT ON COLUMN messaging_outbox.last_error IS '最近一次发布失败的受限摘要';
COMMENT ON COLUMN messaging_outbox.created_at IS '消息信封登记时间';
COMMENT ON COLUMN messaging_outbox.updated_at IS '发布状态最近更新时间，由数据库触发器维护';
COMMENT ON COLUMN messaging_outbox.sent_at IS 'RabbitMQ 确认发布成功的时间';
COMMENT ON COLUMN messaging_outbox.version IS '发布状态并发修改使用的乐观版本';

CREATE TRIGGER set_messaging_outbox_updated_at
    BEFORE UPDATE ON messaging_outbox
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

COMMENT ON COLUMN messaging_outbox.outbox_id IS '单次代理发布记录标识，与业务消息标识分离';
COMMENT ON COLUMN messaging_outbox.target_handler_code IS '定向重试的 Handler 编码，NULL 表示普通消息分发';
COMMENT ON COLUMN messaging_outbox.handler_attempt IS '目标 Handler 要开始的消费尝试序号';
COMMENT ON COLUMN messaging_outbox.handler_retry_rule IS '消费重试链首次登记时冻结的版本化规则';
