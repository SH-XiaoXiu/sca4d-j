DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM messaging_outbox
        WHERE status <> 'SENT'
    ) THEN
        RAISE EXCEPTION
            '升级 MessageBus 前必须先由旧版本发送完 messaging_outbox 中的 PENDING、SENDING 和 FAILED 记录';
    END IF;
END
$$;

ALTER TABLE messaging_outbox
    RENAME COLUMN event_code TO message_code;

COMMENT ON COLUMN messaging_outbox.message_code IS 'Message 声明的稳定 TreeCode；使用斜杠分层';
COMMENT ON COLUMN messaging_outbox.envelope IS 'RabbitMQ 业务消息信封；升级前已发送记录可以保留 v1，新记录使用 v2';

ALTER TABLE notification_template
    RENAME TO notification_inbox_template;
ALTER INDEX uk_notification_template_code
    RENAME TO uk_notification_inbox_template_code;
ALTER TRIGGER set_notification_template_updated_at ON notification_inbox_template
    RENAME TO set_notification_inbox_template_updated_at;

COMMENT ON TABLE notification_inbox_template IS '保存 Inbox 渠道使用的多语言 Tiptap JSON 模板';
COMMENT ON COLUMN notification_inbox_template.content IS 'Locale 到 title/body ProseMirror 文档的 JSON object';
COMMENT ON COLUMN notification_inbox_template.enabled IS '模板是否参与 Inbox 投递';

CREATE TABLE notification_email_template (
    id BIGINT PRIMARY KEY,
    notification_code VARCHAR(128) NOT NULL,
    locales JSONB NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_notification_email_template_code
    ON notification_email_template (notification_code) WHERE deleted = 0;

COMMENT ON TABLE notification_email_template IS '保存 Email 渠道使用的多语言主题、纯文本和 HTML 源码模板';
COMMENT ON COLUMN notification_email_template.notification_code IS 'NotificationType 声明的稳定通知编码';
COMMENT ON COLUMN notification_email_template.locales IS 'Locale 到 subject、text、html 源码的 JSON object';
COMMENT ON COLUMN notification_email_template.enabled IS '模板是否参与 Email 投递';
COMMENT ON COLUMN notification_email_template.version IS '模板并发修改使用的乐观版本';
COMMENT ON COLUMN notification_email_template.deleted IS '逻辑删除标记；0 表示保留，1 表示已删除';

CREATE TRIGGER set_notification_email_template_updated_at
    BEFORE UPDATE ON notification_email_template
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

CREATE FUNCTION sca4dj_migrate_tree_code(value TEXT)
RETURNS TEXT
LANGUAGE SQL
IMMUTABLE
STRICT
RETURN upper(regexp_replace(replace(replace(btrim(value), '.', '/'), '-', '_'), '/+', '/', 'g'));

UPDATE messaging_outbox
SET message_code = sca4dj_migrate_tree_code(message_code);

UPDATE notification_inbox_template
SET notification_code = sca4dj_migrate_tree_code(notification_code);

UPDATE notification_inbox_message
SET notification_code = sca4dj_migrate_tree_code(notification_code);

UPDATE notification_sms_binding
SET notification_code = sca4dj_migrate_tree_code(notification_code);

UPDATE iot_downlink_message
SET acknowledgement_policy = CASE acknowledgement_policy
        WHEN 'device-result' THEN 'IOT/DOWNLINK/ACK/DEVICE/RESULT'
        WHEN 'no-ack' THEN 'IOT/DOWNLINK/ACK/NONE'
        WHEN 'reported-equals-desired' THEN 'IOT/DOWNLINK/ACK/REPORTED/EQUALS/DESIRED'
        ELSE acknowledgement_policy
    END,
    retry_policy = CASE retry_policy
        WHEN 'command-default' THEN 'IOT/DOWNLINK/RETRY/COMMAND/DEFAULT'
        WHEN 'state-sync-default' THEN 'IOT/DOWNLINK/RETRY/STATE/SYNC/DEFAULT'
        WHEN 'no-retry' THEN 'IOT/DOWNLINK/RETRY/NONE'
        ELSE retry_policy
    END,
    last_ack_code = CASE last_ack_code
        WHEN 'reported-equals-desired' THEN 'IOT/DOWNLINK/ACK/REPORTED/EQUALS/DESIRED'
        ELSE last_ack_code
    END
WHERE acknowledgement_policy IN ('device-result', 'no-ack', 'reported-equals-desired')
   OR retry_policy IN ('command-default', 'state-sync-default', 'no-retry')
   OR last_ack_code = 'reported-equals-desired';

UPDATE job.job_run
SET job_key = sca4dj_migrate_tree_code(job_key),
    schedule_key = CASE
        WHEN schedule_key IS NULL THEN NULL
        ELSE sca4dj_migrate_tree_code(schedule_key)
    END;

UPDATE audit_record
SET operation = sca4dj_migrate_tree_code(operation);

DROP FUNCTION sca4dj_migrate_tree_code(TEXT);
