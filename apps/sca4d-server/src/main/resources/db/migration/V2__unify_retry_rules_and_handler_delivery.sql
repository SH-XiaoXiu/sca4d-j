-- 旧 Job 的次数与固定间隔转换成版本化规则，保留已开始的尝试和租约。
ALTER TABLE job.job_run_node ADD COLUMN retry_rule JSONB;
UPDATE job.job_run_node
SET retry_rule = jsonb_build_object('version', 1, 'maxAttempts', max_attempts,
    'backoff', jsonb_build_object('type', 'FIXED', 'delayMillis', retry_delay_millis));
ALTER TABLE job.job_run_node ALTER COLUMN retry_rule SET NOT NULL;
ALTER TABLE job.job_run_node DROP COLUMN max_attempts;
ALTER TABLE job.job_run_node DROP COLUMN retry_delay_millis;
ALTER TABLE job.job_run_node ADD COLUMN max_attempts BIGINT
    GENERATED ALWAYS AS ((retry_rule ->> 'maxAttempts')::BIGINT) STORED;
ALTER TABLE job.job_run_node ALTER COLUMN attempt_count TYPE BIGINT;
ALTER TABLE job.job_run_attempt ALTER COLUMN attempt_number TYPE BIGINT;
COMMENT ON COLUMN job.job_run_node.retry_rule IS '节点创建时冻结的版本化重试次数与退避规则';
COMMENT ON COLUMN job.job_run_node.max_attempts IS '由重试规则派生的总尝试上限，NULL 表示无限重试';

-- 一条业务消息可以有多条代理发布记录，原业务消息身份不变。
ALTER TABLE messaging_outbox ADD COLUMN outbox_id BIGINT;
UPDATE messaging_outbox SET outbox_id = message_id;
ALTER TABLE messaging_outbox ALTER COLUMN outbox_id SET NOT NULL;
ALTER TABLE messaging_outbox DROP CONSTRAINT messaging_outbox_pkey;
ALTER TABLE messaging_outbox ADD PRIMARY KEY (outbox_id);
ALTER TABLE messaging_outbox ADD COLUMN target_handler_code VARCHAR(128);
ALTER TABLE messaging_outbox ADD COLUMN handler_attempt BIGINT;
ALTER TABLE messaging_outbox ADD COLUMN handler_retry_rule JSONB;
ALTER TABLE messaging_outbox ALTER COLUMN attempt_count TYPE BIGINT;
CREATE UNIQUE INDEX uk_messaging_outbox_message ON messaging_outbox (message_id)
    WHERE target_handler_code IS NULL;
CREATE UNIQUE INDEX uk_messaging_outbox_handler_attempt
    ON messaging_outbox (target_handler_code, message_id, handler_attempt)
    WHERE target_handler_code IS NOT NULL;
DROP INDEX idx_messaging_outbox_dispatch;
DROP INDEX idx_messaging_outbox_lease;
DROP INDEX idx_messaging_outbox_sent;
CREATE INDEX idx_messaging_outbox_dispatch ON messaging_outbox (available_at, outbox_id)
    WHERE status IN ('PENDING', 'FAILED');
CREATE INDEX idx_messaging_outbox_lease ON messaging_outbox (lease_until, outbox_id)
    WHERE status = 'SENDING';
CREATE INDEX idx_messaging_outbox_sent ON messaging_outbox (sent_at, outbox_id)
    WHERE status = 'SENT';
COMMENT ON COLUMN messaging_outbox.outbox_id IS '单次代理发布记录标识，与业务消息标识分离';
COMMENT ON COLUMN messaging_outbox.target_handler_code IS '定向重试的 Handler 编码，NULL 表示普通消息分发';
COMMENT ON COLUMN messaging_outbox.handler_attempt IS '目标 Handler 要开始的消费尝试序号';
COMMENT ON COLUMN messaging_outbox.handler_retry_rule IS '消费重试链首次登记时冻结的版本化规则';
